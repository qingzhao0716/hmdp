package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        // 1.从 redis查询商铺类型缓存
        List<String> shopTypeJsons = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY,0,-1);
        // 2.判断是否存在
        if (CollectionUtil.isNotEmpty(shopTypeJsons)) {
            // 3.存在，使用 stream流将 json集合转为 bean集合
            List<ShopType> shopTypeList = shopTypeJsons.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(shopTypeList);
        }
        // 4.不存在，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        // 5.数据库中不存在，缓存空值，防止缓存穿透
        if (CollectionUtil.isEmpty(shopTypes)) {
            stringRedisTemplate.delete(CACHE_SHOP_TYPE_KEY);
            // 存入一个空 List
            stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY, new ArrayList<>());
            stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY, CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回空列表
            return Result.ok(new ArrayList<>());
        }
        // 6.存在，先清空旧缓存，再写入 redis，使用 stream流将 bean集合转为 json集合
        stringRedisTemplate.delete(CACHE_SHOP_TYPE_KEY);
        List<String> shopTypeCache = shopTypes.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY,shopTypeCache);
        stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY,CACHE_SHOP_TYPE_TTL,TimeUnit.MINUTES);
        // 7.返回
        return Result.ok(shopTypes);
    }
}
