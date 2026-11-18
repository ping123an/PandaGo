package com.pandago.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 Spring Data Redis 的缓存实现（阶段 9）。
 *
 * <p><b>序列化</b>：key 用 StringRedisTemplate 原生 String；value 用 Jackson JSON。
 * 不用 JDK 序列化，避免类结构变更导致反序列化失败，也便于用 redis-cli 直接排查。
 * 统一注入 Spring 容器的 ObjectMapper，保证日期/金额格式与接口出参一致。</p>
 *
 * <p><b>降级</b>：所有 Redis 操作都包在 try-catch 中。Redis 不可用时
 * get 返回 empty（等价未命中）、写/删静默跳过、isNull 返回 false，
 * 业务层自动回源 MySQL，核心交易不受影响。异常只 WARN 一次级别日志，不刷屏。</p>
 *
 * <p><b>空值缓存</b>：用固定的特殊字符串标记"查过但不存在"，配 30 秒短 TTL 防穿透；
 * 该标记不会被当作正常业务对象反序列化。</p>
 *
 * <p>可用 {@code pandago.cache.enabled=false} 关闭（测试/无 Redis 环境默认关闭），
 * 关闭时由 {@link NoOpCacheService} 接管。</p>
 */
@Service
@ConditionalOnProperty(name = "pandago.cache.enabled", havingValue = "true")
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String NULL_MARKER = "__PANDAGO_NULL__";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pandago.cache.key-prefix:pandago:}")
    private String keyPrefix;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        String raw = read(key);
        if (raw == null || NULL_MARKER.equals(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(raw, type));
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败，按未命中处理: key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        String raw = read(key);
        if (raw == null || NULL_MARKER.equals(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(raw, type));
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败，按未命中处理: key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(namespaced(key), objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            degradeWarn("写入缓存失败", key, e);
        }
    }

    @Override
    public void setNull(String key, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(namespaced(key), NULL_MARKER, ttl);
        } catch (Exception e) {
            degradeWarn("写入空值缓存失败", key, e);
        }
    }

    @Override
    public boolean isNull(String key) {
        return NULL_MARKER.equals(read(key));
    }

    @Override
    public void evict(String key) {
        try {
            redisTemplate.delete(namespaced(key));
        } catch (Exception e) {
            degradeWarn("删除缓存失败", key, e);
        }
    }

    @Override
    public void evictByPrefix(String prefix) {
        String fullPrefix = keyPrefix == null || keyPrefix.isBlank() ? prefix : keyPrefix + prefix;
        try {
            Set<String> keys = redisTemplate.keys(fullPrefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            // KEYS 仅在缓存失效时使用；本项目数据量小，避免引入 SCAN 的复杂度。
            degradeWarn("按前缀删除缓存失败", prefix, e);
        }
    }

    private String read(String key) {
        try {
            return redisTemplate.opsForValue().get(namespaced(key));
        } catch (Exception e) {
            degradeWarn("读取缓存失败，回源数据库", key, e);
            return null;
        }
    }

    private String namespaced(String key) {
        return keyPrefix == null || keyPrefix.isBlank() ? key : keyPrefix + key;
    }

    private void degradeWarn(String action, String key, Exception e) {
        log.warn("Redis 降级（{}）: key={}, reason={}", action, key, e.getMessage());
    }
}
