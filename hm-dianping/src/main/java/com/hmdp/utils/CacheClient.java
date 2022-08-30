package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 将任意java对象序列化为json并存储在string类型的key中,并且可以设置过期时间
     * @param key redis的key名称
     * @param value 存储任意java对象
     * @param time 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    /**
     * 写值并设置 逻辑过期时间
     * @param key redis的key名称
     * @param value 存储任意java对象
     * @param time 逻辑过期时间
     * @param unit 时间单位
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存空字符串 解决缓存穿透 缓存空字符串固定时间2分钟
     * @param keyPrefix key前缀
     * @param id 唯一id
     * @param type 返回值类型
     * @param dbFallback 查询数据库函数
     * @param time 逻辑过期时间
     * @param unit 时间单位
     * @param <R> 返回值类型
     * @param <ID> id类型
     * @return 查询的信息
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在商铺信息
        if (StrUtil.isNotBlank(json)) {
            //3.存在,返回
            return JSONUtil.toBean(json, type);
        }
        //判断是否为空字符串  ->   防止缓存穿透
//        if (shopJSON!=null){ //经过上面的if判断过滤后,剩下的只有 null 或者 "" 或者 不可见字符
        if ("".equals(json)){
            return null;
        }
        //4.不存在 ,根据id查询数据库
        R r = dbFallback.apply(id);
        //5.数据库中也不存在,返回错误
        if (r==null){
            //将空字符串写入到缓存中,并设置2分钟的过期时间
            stringRedisTemplate.opsForValue().set(key,"",SHOP_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6.数据库中存在,写入到redis中,并设置超时时间
        this.set(key,r,time,unit);
        return r;
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决 缓存击穿
     * @param keyPrefix key前缀
     * @param id 唯一id
     * @param type 返回值类型
     * @param dbFallback 查询数据库函数
     * @param time 逻辑过期时间
     * @param unit 时间单位
     * @param <R> 返回值类型
     * @param <ID> id类型
     * @return 查询的信息
     */
    public <R, ID> R queryWithLogicExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1.从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在商铺信息
        if (StrUtil.isBlank(json)) {
            //3.不存在返回null
            return null;
        }
        //存在 反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期,直接返回
            return r;
        }
        //已过期,执行重建
        //缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取锁成功
        if (isLock){
            //开启独立线程执行重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存 设置逻辑过期时间为30分钟
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicExpire(key,r1,time,unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //返回过期的信息
        return r;
    }
    /**
     * 添加锁 如果不存在
     * @param key 锁名
     * @return boolean
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key 锁名
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
