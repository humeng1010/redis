package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //缓存空字符串解决 缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //互斥锁解决 缓存击穿
//        Shop shop = queryWithMutex(id);
        //逻辑过期解决 缓存击穿
        Shop shop = queryWithLogicExpire(id);
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicExpire(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在商铺信息
        if (StrUtil.isBlank(shopJSON)) {
            //3.不存在返回null
            return null;
        }
        //存在 反序列化
        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期,直接返回
            return shop;
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
                    //创建缓存 设置逻辑过期时间为30分钟
                    saveShop2Redis(id,30L*60);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }

        //返回过期的信息
        return shop;
    }

    /**
     * 执行逻辑过期数据的重建
     * @param id 商品id
     * @param expireSeconds 多少秒后过期
     */
    public void saveShop2Redis(Long id, Long expireSeconds){
        //根据id查询数据库
        Shop shop = this.getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        //逻辑过期
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 互斥锁 解决缓存穿击
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在商铺信息
        if (StrUtil.isNotBlank(shopJSON)) {
            //3.存在,返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return shop;
        }
        //判断是否为空字符串  ->   防止缓存穿透
//        if (shopJSON!=null){ //经过上面的if判断过滤后,剩下的只有 null 或者 "" 或者 不可见字符
        if ("".equals(shopJSON)){
            return null;
        }

        //TODO 开始实现缓存重建
        String lockKey= LOCK_SHOP_KEY+id;
        Shop shop;
        try {
            //1.获取互斥锁
            boolean isLock = tryLock(lockKey);
            //2.判断是否获取成功
            if (!isLock){
                //获取锁失败,休眠,并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //region Description DoubleCheck
            //TODO 再次检查redis缓存是否存在 DoubleCheck
            // 防止第一个线程释放锁之后第二个线程立马获取到了锁(第二个线程前面的redis缓存已经检查过了发现没有缓存,但是此时第一个线程已经重建了缓存)
            // 防止重复更新缓存
            //1.从redis中查询商铺缓存
            String doubleCheckShopJSON = stringRedisTemplate.opsForValue().get(key);
            //2.判断是否存在商铺信息
            if (StrUtil.isNotBlank(doubleCheckShopJSON)) {
                //3.存在,返回
                return JSONUtil.toBean(doubleCheckShopJSON, Shop.class);
            }
            //判断是否为空字符串  ->   防止缓存穿透
//        if (shopJSON!=null){ //经过上面的if判断过滤后,剩下的只有 null 或者 "" 或者 不可见字符
            if ("".equals(doubleCheckShopJSON)){
                return null;
            }
            //endregion

            //TODO 获取锁成功 重建缓存数据
            //4.如果成功,根据id查询数据库
            //根据id查询数据库
            shop = this.getById(id);
            //5.数据库中也不存在,返回错误
            if (shop==null){
                //将空字符串写入到缓存中,并设置2分钟的过期时间
                stringRedisTemplate.opsForValue().set(key,"",SHOP_NULL_TTL,TimeUnit.MINUTES);

                return null;
            }
            //6.数据库中存在,写入到redis中,并设置超时时间
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key,jsonStr,SHOP_ID_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //TODO 释放互斥锁
            unLock(lockKey);

        }
        // 返回
        return shop;

    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在商铺信息
        if (StrUtil.isNotBlank(shopJSON)) {
            //3.存在,返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return shop;
        }
        //判断是否为空字符串  ->   防止缓存穿透
//        if (shopJSON!=null){ //经过上面的if判断过滤后,剩下的只有 null 或者 "" 或者 不可见字符
        if ("".equals(shopJSON)){
            return null;
        }
        //4.不存在 ,根据id查询数据库
        Shop shop = this.getById(id);
        //5.数据库中也不存在,返回错误
        if (shop==null){
            //将空字符串写入到缓存中,并设置2分钟的过期时间
            stringRedisTemplate.opsForValue().set(key,"",SHOP_NULL_TTL,TimeUnit.MINUTES);

            return null;
        }
        //6.数据库中存在,写入到redis中,并设置超时时间
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,SHOP_ID_TTL, TimeUnit.MINUTES);
        return shop;
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
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 实现数据库和缓存数据的读写一致
     * @param shop
     * @return
     */
    @Override
    @Transactional//开启事务,保证出现异常可以回滚数据
    public Result update(Shop shop) {
        if (shop.getId()==null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        this.updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
