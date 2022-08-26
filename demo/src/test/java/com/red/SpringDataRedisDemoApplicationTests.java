package com.red;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
class SpringDataRedisDemoApplicationTests {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testString() {
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        ops.set("name","虎哥");
        Object name = ops.get("name");
        System.out.println(name);
    }

}
