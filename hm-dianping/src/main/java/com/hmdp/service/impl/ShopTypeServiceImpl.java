package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_ID_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryType() {
        //从redis中获取数据
        String cacheShopType = stringRedisTemplate.opsForValue().get(SHOP_ID_KEY);
        //如果缓存中有数据
        if (StrUtil.isNotBlank(cacheShopType)){
//            List typeList = JSONUtil.toBean(cacheShopType, List.class);
            List<ShopType> shopTypes = JSONUtil.toList(cacheShopType, ShopType.class);
            return Result.ok(shopTypes);
        }
        //缓存中没有数据
        //从数据库中查询
        List<ShopType> typeList = this.query().orderByAsc("sort").list();
        if (typeList==null){
            return Result.fail("暂无商品分类");
        }
        //缓存到redis中
        String jsonStr = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(SHOP_ID_KEY,jsonStr);
        return null;
    }
}
