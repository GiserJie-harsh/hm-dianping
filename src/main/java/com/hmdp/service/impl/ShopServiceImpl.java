package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
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
     * @return 根据id查询缓存并放入数据库中去，同时解决缓存穿透、击穿问题（互斥锁）
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
        //判断是不是""，如果是则证明这是可能导致缓存穿透的值
        if(s != null){
            return Result.fail("没有该店铺！");
        }
        //如果不是则证明只是单纯地redis中没有该数据，去数据库查然后重建缓存就行，首先解决缓存击穿问题
        String keyForLock = "lock:shop:" + id;//为某个店铺的锁设置一个key
        Shop shopById = null;//从数据库中查询数据
        try {
            //3.获取互斥锁
            boolean isLock = tryLock(keyForLock);
            //3.1失败，证明已经有线程帮我们进行缓存重建，这里进行休眠后再重新访问
            if(!isLock){
                Thread.sleep(10);//休息一会，等待前面的线程缓存重建完毕时再重新访问
                return QueryById(id);
            }
            //3.2成功，进行缓存重建
            shopById = getById(id);
            Thread.sleep(200);//模拟重建延迟
            if (shopById == null){
                //数据库中不存在则加入值为""的缓存并设置超时时间，防止redis内存不足（缓存穿透）
                redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在");
            }
            //5.数据库中存在则写入redis
            redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopById),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(keyForLock);
        }
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

    /**
     * 获取互斥锁解决缓存击穿问题,采用setnx原理实现
     * @param key
     * @return boolean
     * @author czj
     * @date 2022/12/4 9:02
     */

    private boolean tryLock(String key){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }
    /**
     * 释放互斥锁
     * @param key
     * @return boolean
     * @author czj
     * @date 2022/12/4 9:02
     */

    private void unLock(String key){
        redisTemplate.delete(key);
    }
}
