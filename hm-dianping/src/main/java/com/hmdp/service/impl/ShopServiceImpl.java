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

import javax.annotation.Resource;

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
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJSON)) {
            //3.存在,返回
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            return Result.ok(shop);
        }
        //4.不存在 ,根据id查询数据库
        Shop shop = this.getById(id);
        //5.数据库中也不存在,返回错误
        if (shop==null){
            return Result.fail("店铺不存在");
        }
        //6.数据库中存在,写入到redis中
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
        return Result.ok(shop);
    }
}
