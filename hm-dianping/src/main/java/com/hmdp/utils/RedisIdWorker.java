package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Redis id 生成器
 */
@Component
public class RedisIdWorker {
/*    LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
      long second = time.toEpochSecond(ZoneOffset.UTC);
*/
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();//当前的秒数
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1获取当前日期,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        long increment = ops.increment("icr:" + keyPrefix + ":" + date);

        //3.拼接返回
        //将时间戳左移32位 在使用或运算
        return timestamp << COUNT_BITS | increment;
    }


}
