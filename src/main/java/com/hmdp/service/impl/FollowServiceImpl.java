package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result add_watch(Long id, boolean isFollow) {
        UserDTO user = UserHolder.getUser();//获取当前登录用户
        String key = "follows:" + user.getId();//当前用户
        //判断是关注还是取消关注
        if (isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(user.getId());
            follow.setFollowUserId(id);
            boolean flag = save(follow);
            if(flag){
                redisTemplate.opsForSet().add(key,id.toString());//添加到Set集合，为共同关注功能做准备
            }
        }else {
            // 3.取关，删除 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean flag_1 = remove(new QueryWrapper<Follow>()
                    .eq("user_id", user.getId()).eq("follow_user_id", id));
            if (flag_1){
                redisTemplate.opsForSet().remove(key,id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result commonWatch(Long id) {
        String keyForLogin = "follows:" + UserHolder.getUser().getId();
        String keyForWatch = "follows:" + id;
        //取二者关注的交集，返回共同关注的用户
        Set<String> intersect = redisTemplate.opsForSet().intersect(keyForLogin, keyForWatch);
        if (intersect == null || intersect.isEmpty()){
            //二者没有共同关注的好友
            return Result.ok(Collections.emptyList());//返回一个空集合
        }
        //有共同关注，将其从set解析成list
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);//分别查询以这些用户id为id的用户
        List<UserDTO> userDTOS = users.stream()//转换成DTO
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询是否关注 select count(*) from tb_follow where user_id = ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        // 3.判断
        return Result.ok(count > 0);
    }
}
