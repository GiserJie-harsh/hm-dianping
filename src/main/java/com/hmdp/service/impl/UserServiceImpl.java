package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 用户登陆，向前端返回token
     * @param loginForm
     * @param session
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/2 8:55
     */

    @Override
    public Result userLogin(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //1.判断手机号格式是否正确
        if (RegexUtils.isPhoneInvalid(phone)){
            //2.手机号格式不正确
            Result.fail("手机号格式错误");
        }
        //2.验证验证码是否正确,从redis中取
        String code = loginForm.getCode();//用户输入的验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(cacheCode == null || !cacheCode.equals(code)){
            //3.不正确返回错误信息
            Result.fail("验证码错误");
        }
        //4.正确，查询用户是否在数据库中
        User user = query().eq("phone", phone).one();
        if(user == null){
            //不存在则创建用户
            user = createByPhone(phone);
        }
        //5.保存用户到redis中,这里的key使用token代替，不能用手机号的原因是避免用户敏感信息泄露
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String uuid = UUID.randomUUID().toString(true);
        //将userDTO转化为hash类型，并且由于使用的stringRedisTemplate，所以要求key和value都为string，所以
        //这里也进行了转换
        Map<String, Object> userWithMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        String token = LOGIN_USER_KEY + uuid;
        stringRedisTemplate.opsForHash().putAll(token,userWithMap);
        stringRedisTemplate.expire(token,LOGIN_USER_TTL, TimeUnit.SECONDS);//设置过期时间
        return Result.ok(uuid);//将Token返回给前端
    }

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return com.hmdp.dto.Result
     * @author czj
     * @date 2022/12/2 8:56
     */

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.手机号格式不正确
            Result.fail("手机号格式错误");
        }
        //3.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.将验证码保存至Redis，以便稍后校验用户的验证码输入是否正确，并且设置验证码过期时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL);
        //5.利用第三方平台发送短信验证码，这里暂时不写
        log.debug("验证码为：{}",code);
        return Result.ok();
    }

    @Override
    public Result userSign() {
        // 1.获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;//该用户在keySuffix这个月的签到情况
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );//
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * 创建新用户
     * @param phone
     * @return com.hmdp.entity.User
     * @author czj
     * @date 2022/12/2 8:56
     */

    private User createByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_" + RandomUtil.randomString(10));
        //保存用户到数据库
        save(user);
        return user;
    }
}
