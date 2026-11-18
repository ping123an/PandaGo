package com.pandago.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 无缓存实现（阶段 9）：当 {@code pandago.cache.enabled=false} 时接管。
 *
 * <p>用于没有 Redis 的本地/测试环境：所有读都未命中，业务层每次回源 MySQL。
 * 这样"无 Redis"等价于"Redis 不可用降级"，验证缓存永远不是核心数据的唯一来源。</p>
 */
@Service
@ConditionalOnProperty(name = "pandago.cache.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpCacheService implements CacheService {

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> get(String key, TypeReference<T> type) {
        return Optional.empty();
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        // no-op
    }

    @Override
    public void setNull(String key, Duration ttl) {
        // no-op
    }

    @Override
    public boolean isNull(String key) {
        return false;
    }

    @Override
    public void evict(String key) {
        // no-op
    }

    @Override
    public void evictByPrefix(String prefix) {
        // no-op
    }
}
