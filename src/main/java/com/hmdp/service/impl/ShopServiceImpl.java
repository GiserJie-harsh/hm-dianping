package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    //定义一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /*
     *
     * @param null
     * @return 使用逻辑过期解决缓存击穿问题
     * @author czj
     * @date 2022/11/25 11:27
     */
    @Override
    public Result QueryById(long id) {
        //1.从redis中查询是否有当前产品缓存
        String key = CACHE_SHOP_KEY + id;
        String s = redisTemplate.opsForValue().get(key);
        //2.判断缓存是否命中，理论来说必定命中
        if (StrUtil.isBlank(s)){
            return null;//未命中则直接返回Null
        }
        //3.先把Json数据反序列化对象
        RedisData data = JSONUtil.toBean(s, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) data.getData(), Shop.class);
        LocalDateTime expireTime = data.getExpireTime();
        //4.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1没过期,返回数据
            return Result.ok(shop);
        }
        //4.2过期，需要进行缓存重建
        //5.缓存重建
        //5.1获取互斥锁
        String keys = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(keys);
        //5.2成功，开启独立线程进行缓存重建
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    this.saveShopToRedis(id,20l);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(keys);
                }
            });

        }
        //5.3失败，返回旧数据
        return Result.ok(shop);
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
/**
 * 进行缓存预热，将热点key提前放入Redis中去
 * @param id
 * @return void
 * @author czj
 * @date 2022/12/5 14:52
 */

    public void saveShopToRedis(long id,long expireTime) throws InterruptedException {
        //1.从数据库中查询店铺数据
        Shop byId = getById(id);
        Thread.sleep(10);//模拟缓存重建延时
        //2.封装数据
        RedisData data = new RedisData();
        data.setData(byId);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //3.写入Redis中
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(data));

    }
}
