package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {
        // 1. 查询redis是否存在
        List<ShopType> range = redisTemplate.opsForList().range(RedisConstants.CACHE_SHOPTYPE_KEY, 0, -1);
        // 2.存在返回
        if (!range.isEmpty()){
            return Result.ok(range);
        }
        // 3.不存在，查询数据库
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        // 4.保存到redis
        redisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOPTYPE_KEY, typeList);
        return Result.ok(typeList);
    }
}
