package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //统一前缀
    private static final String KEY_PREFIX = "lock:";
    //value前缀使用UUDI
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    //初始化加载Lua脚本 就可以不用每次释放锁的时候加载 提升性能
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //静态代码块初始化并设置常量值
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        //key
        String key = KEY_PREFIX + name;
        //value
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
        //Boolean.TRUE.equals(bool);
    }

    /**
     * 基于Lua脚本实现查询锁和释放锁的原子性
     */
    @Override
    public void unlock() {
        //调用Lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId()
                );
    }
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId = ID_PREFIX+Thread.currentThread().getId();
//        //获取锁中的标识
//        String key = KEY_PREFIX + name;
//        String id = stringRedisTemplate.opsForValue().get(key);
//        //判断是否一致
//        if (threadId.equals(id)){
//            //一致释放锁
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//    }
}
