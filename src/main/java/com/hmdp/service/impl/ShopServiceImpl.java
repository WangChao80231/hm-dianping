package com.hmdp.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.vo.RedisVo;
import org.apache.ibatis.javassist.convert.TransformReadField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 1.缓存穿透
//        Shop shop = cacheClient.getWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        // 2.互斥锁缓存击穿
        Shop shop = cacheClient.getWithMutex(CACHE_SHOP_KEY,id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        // 3.逻辑过期缓存击穿
//        Shop shop = cacheClient.getWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,10L,TimeUnit.SECONDS);
        return Result.ok(shop);
    }

//    /**
//     * 逻辑过期缓存击穿
//     *
//     * @param id id
//     * @return {@code Shop}
//     */
//    public Shop queryWithLogicalExpire(Long id){
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        // 1.从redis中查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.不存在，返回
//        if (StringUtils.isEmpty(shopJson)) {
//            return null;
//        }
//        // 3.json反序列化对象
//        RedisVo redisVo = JSONUtil.toBean(shopJson, RedisVo.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisVo.getData(), Shop.class);
//        LocalDateTime expireTime = redisVo.getExpireTime();
//        // 4.判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            // 4.1未过期，返回店铺信息
//            return shop;
//        }
//
//        // 4.2过期，缓存重建
//        // 5获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Boolean flag = tryLock(lockKey);
//        // 5.1判断是否获取锁成功
//        if (flag){
//            // 这里重复代码是为了，某一个线程吧锁释放后，其他线程已经走过了上面的判断，会立马再进入这里，造成重复建缓存
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StringUtils.isEmpty(shopJson)) {
//                return null;
//            }
//            redisVo = JSONUtil.toBean(shopJson, RedisVo.class);
//            shop = JSONUtil.toBean((JSONObject) redisVo.getData(), Shop.class);
//            expireTime = redisVo.getExpireTime();
//            if (expireTime.isAfter(LocalDateTime.now())) {
//                return shop;
//            }
//            // 5.2成功，开启独立线程，实现缓存重建
//            executorService.submit(() -> {
//                try {
//                    saveShop2Redis(id, 10L);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    // 释放锁
//                    unLock(lockKey);
//                }
//            });
//        }
//        return shop;
//    }
//
//    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
//
//
//    /**
//     * 互斥锁缓存击穿
//     *
//     * @param id id
//     * @return {@code Shop}
//     */
//    public Shop queryWithMutex(Long id){
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        // 1.从redis中查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        // 2.存在，返回
//        if (!StringUtils.isEmpty(shopJson)) {
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 不为空，即命中空字符对象
//        if (shopJson != null) {
//            return null;
//        }
//        // 3.缓存重建
//        // 3.1 获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            Boolean isLock = tryLock(lockKey);
//            // 3.1.1 判断是否获取成功
//            if(!isLock){
//                // 3.1.2 失败 休眠然后重试
//                Thread.sleep(3000);
//                return queryWithMutex(id);
//            }
//            Thread.sleep(200);
//            // 3.1.2 成功 查询数据库
//            shop = this.getById(id);
//            // 不存在，即命中空字符对象
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 5.存在，保存到redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        }catch (Exception e){
//            e.printStackTrace();
//        }finally {
//            unLock(lockKey);
//        }
//        // 6. 释放互斥锁
//        return shop;
//    }
//
//    /**
//     * 缓存穿透
//     *
//     * @param id id
//     * @return {@code Shop}
//     */
//    public Shop queryWithPassThrough(Long id){
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        // 1.从redis中查询商品
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        // 2.存在，返回
//        if (!StringUtils.isEmpty(shopJson)) {
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        // 不为空，即命中空字符对象
//        if (shopJson != null) {
//            return null;
//        }
//        // 3.null 查询数据库
//        Shop shop = this.getById(id);
//        // 为空，即命中空字符对象
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        // 4.存在，保存到redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }
//
//    private Boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }


    public void saveShop2Redis(Long id,Long expireTime){
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 查询店铺数据
        Shop shop = this.getById(id);
        // 封装逻辑过期
        RedisVo redisVo = new RedisVo();
        redisVo.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        redisVo.setData(shop);
        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisVo));
    }
    @Transactional
    @Override
    public Result update(Shop shop) {
        Assert.notNull(shop.getId(),"id不能为空");
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
