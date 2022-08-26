package com.red.jedis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {
    private static final JedisPool jedisPool;
    static {
        //配置连接池
        JedisPoolConfig config = new JedisPoolConfig();
        //最大连接池数量
        config.setMaxTotal(8);
        //空闲最大连接池数量
        config.setMaxIdle(8);
        //空闲最小连接
        config.setMinIdle(0);
        //当没有连接池是否要等待,等待多长时间,默认值-1无限等待
        config.setMaxWaitMillis(1000);//最多等待1秒

        jedisPool = new JedisPool(config,"localhost",6379,1000);
    }
    public static Jedis getJedis(){
        return jedisPool.getResource();
    }
}
