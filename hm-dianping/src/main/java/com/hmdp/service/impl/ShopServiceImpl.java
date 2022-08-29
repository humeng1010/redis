package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SHOP_ID_TTL;
import static com.hmdp.utils.RedisConstants.SHOP_NULL_TTL;

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
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //1.从redis中查询商铺缓存
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在商铺信息
        if (StrUtil.isNotBlank(shopJSON)) {
            //3.存在,返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return Result.ok(shop);
        }
        //判断是否为空字符串  ->   防止缓存穿透
//        if (shopJSON!=null){ //经过上面的if判断过滤后,剩下的只有 null 或者 "" 或者 不可见字符
        if ("".equals(shopJSON)){
            return Result.fail("查询的商铺不存在");
        }
        //4.不存在 ,根据id查询数据库
        Shop shop = this.getById(id);
        //5.数据库中也不存在,返回错误
        if (shop==null){
            //将空字符串写入到缓存中,并设置2分钟的过期时间
            stringRedisTemplate.opsForValue().set(key,"",SHOP_NULL_TTL,TimeUnit.MINUTES);

            return Result.fail("店铺不存在");
        }
        //6.数据库中存在,写入到redis中,并设置超时时间
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,SHOP_ID_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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
