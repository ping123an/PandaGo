package com.pandago.cache;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Optional;

/**
 * 缓存抽象（阶段 9）。
 *
 * <p>业务层只依赖本接口，不直接依赖 RedisTemplate：好处是
 * 1) Redis 不可用时可以在实现内部降级，业务代码无需到处 try-catch；
 * 2) 测试可注入轻量实现或用 Mockito 模拟命中/未命中/异常，无需真实 Redis；
 * 3) 缓存技术若更换，业务层零改动。</p>
 *
 * <p><b>核心原则</b>：缓存只是加速读的旁路，核心业务数据仍以 MySQL 为准。
 * 任何缓存读取失败都必须等价于"未命中"，由调用方回源数据库。</p>
 */
public interface CacheService {

    /** 读缓存并反序列化为指定类型；未命中 / 空值标记 / Redis 异常均返回 empty。 */
    <T> Optional<T> get(String key, Class<T> type);

    /** 读缓存并反序列化为泛型集合（如 List&lt;ProductListVO&gt;）。 */
    <T> Optional<T> get(String key, TypeReference<T> type);

    /** 写入缓存并设置 TTL；Redis 异常时静默跳过（降级）。 */
    void set(String key, Object value, Duration ttl);

    /**
     * 写入短 TTL 空值标记，防止缓存穿透（不存在的商品被反复打到数据库）。
     * 之后 get 会返回 empty，可配合 {@link #isNull(String)} 区分"查过但不存在"。
     */
    void setNull(String key, Duration ttl);

    /** 判断 key 是否为"查过但不存在"的空值标记；Redis 异常时返回 false。 */
    boolean isNull(String key);

    /** 删除单个 key；Redis 异常时静默跳过。 */
    void evict(String key);

    /** 按前缀批量删除（如商品更新时清理 product:hot:*）；Redis 异常时静默跳过。 */
    void evictByPrefix(String prefix);
}
