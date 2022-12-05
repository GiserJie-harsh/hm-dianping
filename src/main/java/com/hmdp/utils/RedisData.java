package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;
/**
 *
 * @param null
 * @return 使用逻辑过期的方式解决缓存击穿时新定义的实体类，将数据放入data中，缓存逻辑过期时间放入
 * expireTime中
 * @author czj
 * @date 2022/12/5 14:50
 */

@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
