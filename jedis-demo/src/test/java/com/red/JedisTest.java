package com.red;

import com.red.jedis.util.JedisConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class JedisTest {
    private Jedis jedis;

    @Before
    public void setUp(){
        //建立连接
//        jedis = new Jedis("127.0.0.1",6379);
        jedis = JedisConnectionFactory.getJedis();
        //设置密码

        //选择库
        jedis.select(0);
    }

    @Test
    public void testString(){
        String result = jedis.set("name", "虎哥");
        System.out.println("result="+result);

        String name = jedis.get("name");
        System.out.println("name="+name);
    }

    @Test
    public void testHash() {
        //插入Hash值
        jedis.hset("user:1","name","jack");
        jedis.hset("user:1","age","21");
        //取值
        Map<String, String> map = jedis.hgetAll("user:1");
        System.out.println(map);
    }

    @After
    public void tearDown(){
        if (jedis!=null){
            jedis.close();
        }
    }
}
