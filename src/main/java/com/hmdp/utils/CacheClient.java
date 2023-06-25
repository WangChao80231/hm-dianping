package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.vo.RedisVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  缓存工具类
 * </p>
 *
 * @author wangchao
 * @date 2023/6/23 00:46
 */
@Component
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object object, Long expireTime, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object), expireTime, timeUnit);
    }

    public void setWithLogicExpire(String key, Object object, Long expireTime, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透
     *
     * @param prefix     前缀
     * @param id         id
     * @param tClass     t类
     * @param function   函数
     * @param expireTime 到期时间
     * @param timeUnit   时间单位
     * @return {@code T}
     */
    public <T,ID> T getWithPassThrough(String prefix, ID id, Class<T> tClass, Function<ID,T> function, Long expireTime, TimeUnit timeUnit){
        String key = prefix + id;
        // 1.从redis中查询商品
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.存在，返回
        if (!StringUtils.isEmpty(json)) {
            return JSONUtil.toBean(json, tClass);
        }
        // 不为空，即命中空字符对象
        if (json != null) {
            return null;
        }
        // 3.null 查询数据库
        T t = function.apply(id);
        // 为空，即命中空字符对象
        if (t == null) {
            stringRedisTemplate.opsForValue().set(key, "", expireTime, timeUnit);
            return null;
        }
        // 4.存在，保存到redis
        this.set(key, t, expireTime, timeUnit);
        return t;
    }

    /**
     * 逻辑删除缓存击穿
     *
     * @param prefix   前缀
     * @param id       id
     * @param tClass   t类
     * @param function 函数
     * @param time     时间
     * @param timeUnit 时间单位
     * @return {@code T}
     */
    public <T,ID> T getWithLogicalExpire(String prefix, ID id, Class<T> tClass,Function<ID,T> function, Long time, TimeUnit timeUnit){
        String key = prefix + id;
        // 1.从redis中查询商品
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.不存在，返回
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        // 3.json反序列化对象
        RedisVo redisVo = JSONUtil.toBean(json, RedisVo.class);
        T t = JSONUtil.toBean((JSONObject) redisVo.getData(), tClass);
        LocalDateTime expireTime = redisVo.getExpireTime();
        // 4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            // 4.1未过期，返回店铺信息
            return t ;
        }

        // 4.2过期，缓存重建
        // 5获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean flag = tryLock(lockKey);
        // 5.1判断是否获取锁成功
        if (flag){
            // TODO-wangChao 2023/6/23 01:13   这里应该还要再次判断缓存是否存在，避免多次写入缓存
            // 5.2成功，开启独立线程，实现缓存重建
            executorService.submit(() -> {
                try {
                    T t1 = function.apply(id);
                    setWithLogicExpire(key, t1, time, timeUnit);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        return t;
    }

    /**
     * 互斥锁缓存击穿
     *
     * @param keyPrefix  关键前缀
     * @param id         id
     * @param type       类型
     * @param dbFallback db回退
     * @param time       时间
     * @param unit       单位
     * @return {@code R}
     */
    public <R, ID> R getWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 返回一个错误信息
            return null;
        }

        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!isLock) {
                // 4.3.获取锁失败，休眠并重试
                Thread.sleep(50);
                return getWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 4.4.获取锁成功，根据id查询数据库
            r = dbFallback.apply(id);
            // 5.不存在，返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 7.释放锁
            unLock(lockKey);
        }
        // 8.返回
        return r;
    }

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    private Boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
