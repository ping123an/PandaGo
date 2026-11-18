package com.pandago.cache;

import java.time.Duration;

/**
 * 缓存 Key 与 TTL 常量集中管理（阶段 9）。
 *
 * <p>Key 命名遵循 AGENTS 七章约定：product:detail:{id}、category:list、product:hot:{limit}。
 * 热门商品带 limit 作为参数，避免不同 limit 复用同一份缓存串数据。</p>
 *
 * <p>TTL 全部有限值，杜绝永久缓存（AGENTS 6.7）：商品详情/分类 10 分钟，
 * 热门 5 分钟，空值 30 秒（短 TTL 防止穿透又不至于长期掩盖新建数据）。</p>
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    // v2：用户端 VO 不再暴露具体库存，换 key 让改造前的旧缓存自然失效
    public static final String PRODUCT_DETAIL_PREFIX = "product:detail:v2:";
    public static final String CATEGORY_LIST = "category:list";
    public static final String PRODUCT_HOT_PREFIX = "product:hot:v2:";

    public static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    public static final Duration CATEGORY_TTL = Duration.ofMinutes(10);
    public static final Duration HOT_TTL = Duration.ofMinutes(5);
    public static final Duration NULL_TTL = Duration.ofSeconds(30);

    public static String productDetail(Long id) {
        return PRODUCT_DETAIL_PREFIX + id;
    }

    public static String productHot(int limit) {
        return PRODUCT_HOT_PREFIX + limit;
    }
}
