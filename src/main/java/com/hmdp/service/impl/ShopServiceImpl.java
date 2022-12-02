package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    /*
     *
     * @param null
     * @return 根据id查询缓存并放入数据库中去，同时解决缓存穿透问题
     * @author czj
     * @date 2022/11/25 11:27
     */
    @Override
    public Result QueryById(long id) {
        //1.从redis中查询是否有当前产品缓存
        String key = CACHE_SHOP_KEY + id;
        String s = redisTemplate.opsForValue().get(key);
        //2.这一步同时检查了缓存是否命中与是否为空值两个操作
        if (StrUtil.isNotBlank(s)){
            Shop shop = JSONUtil.toBean(s, Shop.class);//将Json转成Java对象返回
            return Result.ok(shop);
        }
        //判断是不是""，如果是则证明这是可能导致缓存穿透的值，如果不是则证明只是单纯地redis中没有
        //该数据，去数据库查就行
        if(s != null){
            return Result.fail("没有该店铺！");
        }
        //3.未命中则从数据库中查询数据
        Shop shopById = getById(id);
        if (shopById == null){
            //数据库中不存在则加入值为""的缓存并设置超时时间，防止redis内存不足
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        //5.数据库中存在则写入redis
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopById),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopById);
    }

    /**
     * 解决缓存一致性问题，在数据更新时先更新数据库，然后删除缓存
     *
     * @param shop
     * @author czj
     * @date 2022/11/26 8:46
     */
    @Override
    public Result update(Shop shop) {
        //首先判断是否有此店铺
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不存在！");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
