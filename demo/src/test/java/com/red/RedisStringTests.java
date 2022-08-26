package com.red;

import com.alibaba.fastjson.JSON;
import com.red.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Map;

@SpringBootTest
class RedisStringTests {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testString() {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.set("name","虎哥");
        Object name = ops.get("name");
        System.out.println(name);
    }

    @Test
    void testObject() {
        //创建对象
        User user = new User("虎哥", 20);
        //手动序列化为JSON 引入fastjson
        String jsonString = JSON.toJSONString(user);
        //存数据
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.set("user:100",jsonString);
        //取数据
        String jsonUser = ops.get("user:100");
        User user1 = JSON.parseObject(jsonUser, User.class);
        System.out.println("user1 = " + user1);


    }

    @Test
    void testHash(){
        HashOperations<String, Object, Object> ops = stringRedisTemplate.opsForHash();
        ops.put("user:400","name","胖虎");
        ops.put("user:400","age","21");

        Map<Object, Object> entries = ops.entries("user:400");
        System.out.println("entries = " + entries);
    }



}
