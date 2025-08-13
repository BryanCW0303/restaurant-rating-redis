package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) throws InterruptedException {

        Shop shop = queryWithLogicalExpire(id);

//        Shop shop = queryWithMutex(id);

        if (shop == null) {
            return Result.fail("The shop is not exist");
        }

        return Result.ok(shop);
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // apply logical expire to solve cache breakdown
    public Shop queryWithLogicalExpire(Long id) throws InterruptedException {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // query on cache, if not hit, save data into cache
        if (StrUtil.isBlank(shopJson)) {
            saveShop2Redis(id, 30L);
            Shop shop = getById(id);
            return shop;
        }

        // if hits, check whether it is expired.
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // if not expired, return shop
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        // if expired, rewrite data into cache
        String lockKey = CACHE_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        return shop;
    }

    // apply mutex lock to solve cache breakdown:
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // get shop information from cache
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // if exists, return shop
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // solve cache penetration: if hit null value, return error
        if (shopJson != null) {
            return null;
        }

        // get mutex lock
        String lockKey = null;
        Shop shop = null;
        try {
            lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);

            // if not lock, sleep and retry later
            if (!isLock) {
                Thread.sleep(10);
                return queryWithMutex(id);
            }
            // get shop information from database
            shop = getById(id);

            // if locks, query by id in database
            // if not exists in database, return error
            if (shop == null) {
                // solve cache penetration: save null value into cache
                stringRedisTemplate.opsForValue().set(
                        key,
                        "",
                        CACHE_NULL_TTL + RandomUtil.randomNumber(),
                        TimeUnit.MINUTES);
                return null;
            }

            // save the shop into cache
            stringRedisTemplate.opsForValue().set(
                    key,
                    JSONUtil.toJsonStr(shop),
                    CACHE_SHOP_TTL + RandomUtil.randomNumber(),
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // release mutex lock
            unlock(lockKey);
        }

        return shop;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    // save logical expire to redis
    private void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


    // update database, delete cache
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("The shop id cannot be empty");
        }
        // update database
        updateById(shop);
        // delete cache
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByTypeId(Integer typeId, Integer current, Double x, Double y) {
        // check if coordinate-based query is needed
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // calculate page parameters
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // query redis
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        if (geoResults == null) {
            return Result.ok(Collections.emptyList());
        }
        // extract shopId
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = geoResults.getContent();
        List<Long> ids = new ArrayList<>();
        Map<String, Distance> distanceMap = new HashMap<>();
        if (from >= content.size()) {
            return Result.ok(Collections.emptyList());
        }
        content.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // query shop by id
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id, " + idsStr + ")").list();
        for (Shop shop: shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // return result
        return Result.ok(shops);
    }
}
