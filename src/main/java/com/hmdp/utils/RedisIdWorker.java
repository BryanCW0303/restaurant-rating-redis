package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final Long TIME_STAMP= 1735689600L;
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Long nextId(String keyPrefix) {
        // generate time stamp
        LocalDateTime now = LocalDateTime.now();
        Long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        Long timeStamp = nowEpochSecond - TIME_STAMP;

        // generate sequence number
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + date);

        // concentrate
        return timeStamp << COUNT_BITS | count;
    }
}
