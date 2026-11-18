package com.pandago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.cache.CacheKeys;
import com.pandago.cache.RedisCacheService;
import com.pandago.product.vo.ProductDetailVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 缓存实现单元测试（阶段 9）。
 *
 * <p>用 Mockito 模拟 StringRedisTemplate，无需真实 Redis 即可验证：
 * 缓存命中、未命中、空值缓存（防穿透）以及 Redis 不可用时的降级行为。
 * 这正对应教学要求"缓存不能作为核心业务数据的唯一来源"。</p>
 */
class Stage9CacheFallbackTests {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisCacheService cache;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        valueOperations = ops;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new RedisCacheService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(cache, "keyPrefix", "pandago:");
    }

    private ProductDetailVO sampleVO(long id, String name) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(id);
        vo.setName(name);
        vo.setPrice(new BigDecimal("99.00"));
        return vo;
    }

    @Test
    void cacheHitReturnsDeserializedValue() throws Exception {
        ProductDetailVO vo = sampleVO(7L, "缓存中的商品");
        when(valueOperations.get("pandago:" + CacheKeys.productDetail(7L)))
                .thenReturn(objectMapper.writeValueAsString(vo));

        Optional<ProductDetailVO> got = cache.get(CacheKeys.productDetail(7L), ProductDetailVO.class);

        assertTrue(got.isPresent());
        assertEquals("缓存中的商品", got.get().getName());
        assertEquals(new BigDecimal("99.00"), got.get().getPrice());
    }

    @Test
    void cacheMissReturnsEmpty() {
        when(valueOperations.get(anyString())).thenReturn(null);
        assertTrue(cache.get(CacheKeys.productDetail(8L), ProductDetailVO.class).isEmpty());
    }

    @Test
    void nullValueCacheMarksMissingProduct() {
        when(valueOperations.get(anyString())).thenReturn("__PANDAGO_NULL__");
        assertTrue(cache.isNull(CacheKeys.productDetail(999L)));
        assertTrue(cache.get(CacheKeys.productDetail(999L), ProductDetailVO.class).isEmpty());
    }

    @Test
    void redisDownGetDegradesToMiss() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        Optional<ProductDetailVO> got = cache.get(CacheKeys.productDetail(9L), ProductDetailVO.class);
        assertTrue(got.isEmpty(), "Redis 不可用时 get 必须等价未命中，交给上层回源 MySQL");
        assertFalse(cache.isNull(CacheKeys.productDetail(9L)));
    }

    @Test
    void redisDownSetAndEvictDoNotThrow() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set(anyString(), anyString(), any());
        doThrow(new RedisConnectionFailureException("down"))
                .when(redisTemplate).delete(anyString());
        when(redisTemplate.keys(anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertDoesNotThrow(() -> cache.set(CacheKeys.productDetail(1L), sampleVO(1L, "x"), CacheKeys.DETAIL_TTL));
        assertDoesNotThrow(() -> cache.setNull(CacheKeys.productDetail(1L), CacheKeys.NULL_TTL));
        assertDoesNotThrow(() -> cache.evict(CacheKeys.productDetail(1L)));
        assertDoesNotThrow(() -> cache.evictByPrefix(CacheKeys.PRODUCT_HOT_PREFIX));
        verify(valueOperations, never()).get(eq("unused"));
    }
}
