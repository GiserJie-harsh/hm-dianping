package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";


    /**
     * 获取redis分布式锁
     * @param timeoutSec
     * @return boolean
     * @author czj
     * @date 2022/12/6 16:25
     */

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标示
        long threadId = Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }
    /**
     * 释放redis分布式锁
     * @return void
     * @author czj
     * @date 2022/12/6 16:25
     */
    @Override
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }

}
