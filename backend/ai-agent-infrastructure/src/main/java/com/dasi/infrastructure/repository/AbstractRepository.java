package com.dasi.infrastructure.repository;

import com.dasi.infrastructure.util.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.infrastructure.repository
 * @Author: xerina
 * @CreateTime: 2026-03-30  19:54
 * @Description: TODO
 */
@Slf4j
public abstract class AbstractRepository {

    @Resource
    private RedisUtil redisUtil;

    /**
     * 通用缓存处理方法
     * 优先从缓存获取，缓存不存在则从数据库获取并写入缓存
     *
     * @param cacheKey      缓存键
     * @param type          返回类型
     * @param dbFallback    数据库查询函数
     * @param <T>           返回类型
     * @return              查询结果
     */
    protected <T> T getFromCacheOrDb(String cacheKey, Class<T> type, Supplier<T> dbFallback) {
        // 从缓存获取
        T cacheResult = redisUtil.getValue(cacheKey, type);
        log.info("从缓存中获取到数据 key:{} ,value:{} ", cacheKey, cacheResult);
        if(cacheResult != null){
            return cacheResult;
        }
        // 如果为空则从数据库中获取
        T dbResult = dbFallback.get();
        // 如果数据库为空
        if(dbResult == null){
            return null;
        }
        redisUtil.setValue(cacheKey, dbResult);
        return dbResult;
    }
}
