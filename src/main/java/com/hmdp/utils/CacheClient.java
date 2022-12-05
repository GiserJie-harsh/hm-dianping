package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @author CZJ
 * @desc redis工具类封装
 * @create 2022-12-05 20:13
 */
@Slf4j
@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate redisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public CacheClient(StringRedisTemplate redisTemplate) {
//        this.redisTemplate = redisTemplate;
//    }
    /**
     *
     * @param null
     * @return 将对象序列化成Json字符串作为值，string类型作为key存入redis
     * @author czj
     * @date 2022/12/5 20:18
     */
    public void set(String key, Object value, long timeOut, TimeUnit unit){
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),timeOut,unit);
    }
/**
 * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓
 * 存击穿问题
 * @param key
 * @param value
 * @param timeOut
 * @param unit
 * @return void
 * @author czj
 * @date 2022/12/5 20:24
 */
    public void setLogicTimeOut(String key, Object value, long timeOut, TimeUnit unit){
        RedisData data = new RedisData();
        data.setData(value);
        data.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeOut)));
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(data));
    }
    
    /**
     *根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param prefix:缓存前缀
     * @param id：查询参数
     * @param type：参数类型
     * @param dbFallback：函数式编程，函数中写明怎么查询数据库
     * @param time：缓存时间
     * @param unit：时间单位
     * @return R
     * @author czj
     * @date 2022/12/5 20:29
     */

    public <R,ID> R queryWithPassThrough(String prefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        //1.从redis中查询是否有当前产品缓存
        String key = prefix + id;
        String json = redisTemplate.opsForValue().get(key);
        //2.这一步同时检查了缓存是否命中与是否为空值两个操作
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);//将Json转成Java对象返回
        }
        //判断是不是""，如果是则证明这是可能导致缓存穿透的值，如果不是则证明只是单纯地redis中没有
        //该数据，去数据库查就行
        if(json != null){
            return null;
        }
        //3.未命中则从数据库中查询数据
        R apply = dbFallback.apply(id);
        if (apply == null){
            //数据库中不存在则加入值为""的缓存并设置超时时间，防止redis内存不足
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5.数据库中存在则写入redis
        this.set(key,apply,time,unit);
        return apply;
    }
    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     * @param keyPrefix 缓存前缀
     * @param id 查询参数
     * @param type 返回值类型
     * @param dbFallback 函数式编程，指定怎么查询数据库
     * @param time 过期时间
     * @param unit 时间单位
     * @return R
     * @author czj
     * @date 2022/12/5 20:41
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = redisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在，直接返回
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return r;
        }
        // 5.2.已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存
                    this.setLogicTimeOut(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return r;
    }
    private boolean tryLock(String key){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }
    private void unlock(String key) {
        redisTemplate.delete(key);
    }
    
}
