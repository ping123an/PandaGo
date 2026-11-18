package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.cart.entity.CartItem;
import com.pandago.cart.mapper.CartItemMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.favorite.entity.Favorite;
import com.pandago.favorite.mapper.FavoriteMapper;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.JwtTokenProvider;
import com.pandago.security.LoginUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 5 购物车、收藏、收货地址集成测试。
 *
 * <p>除并发用例外，每个测试用 @Transactional 自动回滚，不污染初始化数据。
 * 用户身份用 JwtTokenProvider 直接签发 Token，无需额外建测试用户；
 * 商品用 ProductMapper 插入，每个用例用自己的随机名称避免唯一键冲突。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage5UserCenterTests {

    private static final long USER_A = 91001L;
    private static final long USER_B = 91002L;
    private static final String MAIN_IMAGE = "/images/panda-product.svg";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ProductMapper productMapper;
    @Autowired private CartItemMapper cartItemMapper;
    @Autowired private FavoriteMapper favoriteMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String token(long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId, List.of(RoleCode.USER));
    }

    private String uniqueName() {
        return "S5-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private long firstCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    /** 插入一个已审核、上架的可购买商品 */
    private long insertProduct(BigDecimal price, int stock) throws Exception {
        Product product = new Product();
        product.setCategoryId(firstCategoryId());
        product.setMerchantId(0L);
        product.setName(uniqueName());
        product.setMainImage(MAIN_IMAGE);
        product.setPrice(price);
        product.setStock(stock);
        product.setSales(0);
        product.setStatus(ProductStatus.ON_SALE.getValue());
        product.setAuditStatus(AuditStatus.APPROVED.getValue());
        product.setSort(0);
        productMapper.insert(product);
        return product.getId();
    }

    private String addCartBody(long productId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "productId", productId, "quantity", quantity));
    }

    private String addressBody(String receiver, boolean isDefault) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "receiverName", receiver,
                "phone", "13800138000",
                "province", "四川省",
                "city", "成都市",
                "district", "武侯区",
                "detailAddress", "熊猫大道 1 号",
                "isDefault", isDefault));
    }

    private long addCartItem(long userId, long productId, int quantity) throws Exception {
        String body = mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addCartBody(productId, quantity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    // 场景 1：未登录访问购物车 / 收藏 / 地址全部 401
    @Test
    void unauthenticatedAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/favorites")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/addresses")).andExpect(status().isUnauthorized());
    }

    // 场景 2：重复添加同一商品合并数量，库中只有一行
    @Test
    @Transactional
    void addingSameProductTwiceMergesQuantity() throws Exception {
        long productId = insertProduct(new BigDecimal("19.90"), 50);
        addCartItem(USER_A, productId, 2);
        addCartItem(USER_A, productId, 3);

        String cart = mockMvc.perform(get("/api/v1/cart").header("Authorization", token(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5))
                .andReturn().getResponse().getContentAsString();
        JsonNode item = objectMapper.readTree(cart).path("data").path("items").get(0);
        assertEquals(5, item.path("quantity").asInt());
        assertEquals(0, new BigDecimal("19.90").multiply(BigDecimal.valueOf(5))
                .compareTo(item.path("subtotal").decimalValue()));

        Long rows = cartItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, USER_A).eq(CartItem::getProductId, productId));
        assertEquals(1L, rows);
    }

    // 场景 3：下架商品不能加入购物车
    @Test
    @Transactional
    void addingOffShelfProductIsRejected() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 10);
        Product product = productMapper.selectById(productId);
        product.setStatus(ProductStatus.OFF_SALE.getValue());
        productMapper.updateById(product);

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addCartBody(productId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("下架")));

        Long rows = cartItemMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, USER_A).eq(CartItem::getProductId, productId));
        assertEquals(0L, rows);
    }

    // 场景 4：数量 0、负数被参数校验拦截；超过库存被业务规则拦截
    @Test
    @Transactional
    void invalidQuantityIsRejected() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 3);
        long itemId = addCartItem(USER_A, productId, 1);

        for (int invalid : new int[]{0, -1}) {
            mockMvc.perform(put("/api/v1/cart/items/" + itemId)
                            .header("Authorization", token(USER_A))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":" + invalid + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        mockMvc.perform(put("/api/v1/cart/items/" + itemId)
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // 场景 5：不能操作他人的购物车条目
    @Test
    @Transactional
    void cannotOperateOtherUsersCartItem() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 20);
        long itemId = addCartItem(USER_A, productId, 1);

        mockMvc.perform(put("/api/v1/cart/items/" + itemId)
                        .header("Authorization", token(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":9}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
                        .header("Authorization", token(USER_B)))
                .andExpect(status().isNotFound());

        CartItem item = cartItemMapper.selectById(itemId);
        assertEquals(1, item.getQuantity());
    }

    // 场景 6：重复收藏被唯一索引拒绝
    @Test
    @Transactional
    void duplicateFavoriteIsRejected() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 20);
        String payload = objectMapper.writeValueAsString(java.util.Map.of("productId", productId));

        mockMvc.perform(post("/api/v1/favorites").header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/favorites").header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已")));

        assertEquals(1L, favoriteMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, USER_A).eq(Favorite::getProductId, productId)));
    }

    // 场景 7：不能取消他人的收藏
    @Test
    @Transactional
    void cannotRemoveOtherUsersFavorite() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 20);
        mockMvc.perform(post("/api/v1/favorites").header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("productId", productId))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/favorites/" + productId).header("Authorization", token(USER_B)))
                .andExpect(status().isNotFound());

        assertEquals(1L, favoriteMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, USER_A).eq(Favorite::getProductId, productId)));
    }

    // 场景 8：多次设置默认地址，最终只有一个默认
    @Test
    @Transactional
    void repeatedSetDefaultKeepsOnlyOne() throws Exception {
        long first = createAddress(USER_A, "张三", true);
        long second = createAddress(USER_A, "李四", false);
        long third = createAddress(USER_A, "王五", false);

        mockMvc.perform(put("/api/v1/addresses/" + second + "/default").header("Authorization", token(USER_A)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/addresses/" + third + "/default").header("Authorization", token(USER_A)))
                .andExpect(status().isOk());

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/addresses")
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        long defaultCount = 0;
        Long defaultId = null;
        for (JsonNode node : list.path("data")) {
            if (node.path("isDefault").asBoolean()) {
                defaultCount++;
                defaultId = node.path("id").asLong();
            }
        }
        assertEquals(1, defaultCount);
        assertEquals(third, defaultId);
        assertTrue(first > 0);
    }

    // 场景 9：不能修改或删除他人地址
    @Test
    @Transactional
    void cannotUpdateOrDeleteOtherUsersAddress() throws Exception {
        long addressId = createAddress(USER_A, "张三", false);

        mockMvc.perform(put("/api/v1/addresses/" + addressId).header("Authorization", token(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody("黑客", false)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/addresses/" + addressId).header("Authorization", token(USER_B)))
                .andExpect(status().isNotFound());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_address WHERE id = ? AND receiver_name = '张三'", Integer.class, addressId));
    }

    // 场景 10：并发添加同一商品，唯一索引保证只有一行且数量正确累加
    @Test
    void concurrentAddSameProductRespectsUniqueIndex() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 100);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        new LoginUserPrincipal(USER_A, "user-" + USER_A), null,
                                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                        cartService.add(cartAddDto(productId, 1));
                    } catch (Throwable ex) {
                        failures.add(ex);
                    } finally {
                        SecurityContextHolder.clearContext();
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(60, TimeUnit.SECONDS), "并发添加超时");
            assertTrue(failures.isEmpty(), "并发添加出现失败: " + failures);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cart_item WHERE user_id = ? AND product_id = ?",
                    Integer.class, USER_A, productId));
            assertEquals(threads, jdbcTemplate.queryForObject(
                    "SELECT quantity FROM cart_item WHERE user_id = ? AND product_id = ?",
                    Integer.class, USER_A, productId));
        } finally {
            pool.shutdownNow();
            // 断言失败也必须清理，否则残留行会污染后续用同一测试用户 ID 的用例
            jdbcTemplate.update("DELETE FROM cart_item WHERE user_id = ? AND product_id = ?", USER_A, productId);
            jdbcTemplate.update("DELETE FROM product WHERE id = ?", productId);
        }
    }

    // 补充：购物车价格实时取商品表，加购后改价立即反映
    @Test
    @Transactional
    void cartPriceIsReadFromProductTable() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 20);
        addCartItem(USER_A, productId, 2);

        Product product = productMapper.selectById(productId);
        product.setPrice(new BigDecimal("25.00"));
        productMapper.updateById(product);

        JsonNode cart = objectMapper.readTree(mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode item = cart.path("data").path("items").get(0);
        assertEquals(0, new BigDecimal("25.00").compareTo(item.path("price").decimalValue()));
        assertEquals(0, new BigDecimal("50.00").compareTo(item.path("subtotal").decimalValue()));
        assertEquals(0, new BigDecimal("50.00").compareTo(cart.path("data").path("estimatedTotal").decimalValue()));
    }

    // 补充：加购后商品下架，购物车提示重新确认且不计入预估金额
    @Test
    @Transactional
    void offShelfProductInCartIsMarkedInvalid() throws Exception {
        long productId = insertProduct(new BigDecimal("10.00"), 20);
        addCartItem(USER_A, productId, 2);

        Product product = productMapper.selectById(productId);
        product.setStatus(ProductStatus.OFF_SALE.getValue());
        productMapper.updateById(product);

        // MockMvc 的 getContentAsString() 默认按 ISO-8859-1 解码，会乱码中文；
        // 这里按 UTF-8 解码原始字节，与真实 HTTP 客户端的解码方式一致。
        byte[] bytes = mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        JsonNode cart = objectMapper.readTree(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        JsonNode item = cart.path("data").path("items").get(0);
        assertFalse(item.path("available").asBoolean());
        assertTrue(item.path("invalidReason").asText().contains("下架"));
        assertEquals(1, cart.path("data").path("invalidCount").asInt());
        assertEquals(0, cart.path("data").path("estimatedTotal").decimalValue().compareTo(BigDecimal.ZERO));
    }

    // 补充：删除默认地址不自动补位，且之后仍可重新设置默认
    @Test
    @Transactional
    void deletingDefaultAddressDoesNotAutoPromote() throws Exception {
        long first = createAddress(USER_A, "张三", true);
        createAddress(USER_A, "李四", false);

        mockMvc.perform(delete("/api/v1/addresses/" + first).header("Authorization", token(USER_A)))
                .andExpect(status().isOk());

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/addresses")
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, list.path("data").size());
        assertFalse(list.path("data").get(0).path("isDefault").asBoolean());

        long remaining = list.path("data").get(0).path("id").asLong();
        mockMvc.perform(put("/api/v1/addresses/" + remaining + "/default")
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk());
        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/api/v1/addresses")
                        .header("Authorization", token(USER_A))).andReturn().getResponse().getContentAsString());
        assertTrue(after.path("data").get(0).path("isDefault").asBoolean());
    }

    private long createAddress(long userId, String receiver, boolean isDefault) throws Exception {
        String body = mockMvc.perform(post("/api/v1/addresses")
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody(receiver, isDefault)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    @Autowired
    private com.pandago.cart.service.CartService cartService;

    private com.pandago.cart.dto.CartAddDTO cartAddDto(long productId, int quantity) {
        com.pandago.cart.dto.CartAddDTO dto = new com.pandago.cart.dto.CartAddDTO();
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        return dto;
    }
}
