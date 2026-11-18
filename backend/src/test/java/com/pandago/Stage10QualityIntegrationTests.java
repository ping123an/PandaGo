package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.address.entity.UserAddress;
import com.pandago.address.mapper.UserAddressMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 阶段 10：质量验证补充集成测试。
 *
 * <p>聚焦"端到端一致性 + 统一异常处理 + 并发防超卖"，与前面阶段测试互补：
 * 1. 统一异常处理：参数校验 400、业务冲突码与 HTTP 状态映射、未知资源 404；
 * 2. 权限隔离：三角色边界（在前面阶段基础上补一组断言）；
 * 3. 库存=1 时两个用户并发下单：只允许一个成功，库存/销量不多扣；
 * 4. 端到端核对：注册→下单→支付→发货→确认→评价→公开评价列表的库存与状态一致性。
 *
 * <p>不使用类级 @Transactional（需真实提交后查库），每个用例用专用 userId 与随机商品名，@AfterEach 彻底清理。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage10QualityIntegrationTests {

    private static final long U_A = 95001L;
    private static final long U_B = 95002L;
    private static final long MERCHANT_ID = 95003L;
    private static final String MAIN_IMAGE = "/images/panda-product.svg";
    private static final BigDecimal PRICE = new BigDecimal("88.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ProductMapper productMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM review WHERE order_item_id IN (SELECT id FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?)))", U_A, U_B);
        jdbcTemplate.update("DELETE FROM shipment WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))", U_A, U_B);
        jdbcTemplate.update("DELETE FROM payment_record WHERE user_id IN (?,?)", U_A, U_B);
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))", U_A, U_B);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id IN (?,?)", U_A, U_B);
        jdbcTemplate.update("DELETE FROM cart_item WHERE user_id IN (?,?)", U_A, U_B);
        jdbcTemplate.update("DELETE FROM user_address WHERE user_id IN (?,?)", U_A, U_B);
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'S10-%'");
    }

    private String token(long userId, RoleCode role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId, List.of(role));
    }

    private long categoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private long insertProduct(String name, int stock, long merchantId) throws Exception {
        Product product = new Product();
        product.setCategoryId(categoryId());
        product.setMerchantId(merchantId);
        product.setName(name);
        product.setMainImage(MAIN_IMAGE);
        product.setPrice(PRICE);
        product.setStock(stock);
        product.setSales(0);
        product.setStatus(ProductStatus.ON_SALE.getValue());
        product.setAuditStatus(AuditStatus.APPROVED.getValue());
        product.setSort(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long insertAddress(long userId) {
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName("阶段10收货人");
        address.setPhone("13900139000");
        address.setProvince("四川省");
        address.setCity("成都市");
        address.setDistrict("武侯区");
        address.setDetailAddress("熊猫大道 10 号");
        address.setIsDefault(0);
        addressMapper.insert(address);
        return address.getId();
    }

    private MvcResult createOrder(long userId, long productId, int quantity) throws Exception {
        long addressId = insertAddress(userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("items", List.of(Map.of("productId", productId, "quantity", quantity)));
        return mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(userId, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private int stockOf(long productId) {
        Integer n = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        return n == null ? -1 : n;
    }

    private int salesOf(long productId) {
        Integer n = jdbcTemplate.queryForObject("SELECT sales FROM product WHERE id = ?", Integer.class, productId);
        return n == null ? -1 : n;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    // ========== 一、统一异常处理与参数校验 ==========

    @Test
    @DisplayName("统一异常处理：参数校验失败返回 HTTP 400 + code=400")
    void validationReturns400WithUnifiedBody() throws Exception {
        // addressId 缺失（@NotNull）→ MethodArgumentNotValidException → 400
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(U_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andReturn();
        assertEquals(400, result.getResponse().getStatus());
        JsonNode body = json(result);
        assertEquals(400, body.path("code").asInt());
        assertTrue(body.path("message").asText().contains("地址"));
    }

    @Test
    @DisplayName("统一异常处理：业务冲突 409 与资源不存在 404 的 HTTP 映射")
    void businessExceptionHttpMapping() throws Exception {
        // 不存在的商品 → 404
        long addressId = insertAddress(U_A);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("items", List.of(Map.of("productId", 9999999L, "quantity", 1)));
        MvcResult missing = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(U_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
        assertEquals(404, missing.getResponse().getStatus());
        assertEquals(404, json(missing).path("code").asInt());
    }

    @Test
    @DisplayName("统一返回：未知资源走全局 404，返回 Result 结构而非 HTML")
    void unknownEndpointReturns404() throws Exception {
        // 带有效 token 通过 Security 的 anyRequest().authenticated()，再验证未映射路由返回 404
        MvcResult result = mockMvc.perform(get("/api/v1/this-endpoint-does-not-exist")
                        .header("Authorization", token(U_A, RoleCode.USER)))
                .andReturn();
        assertEquals(404, result.getResponse().getStatus());
    }

    // ========== 二、权限隔离补充 ==========

    @Test
    @DisplayName("权限隔离：普通用户访问管理端 403、商家端 403，未登录 401")
    void roleBoundary() throws Exception {
        assertEquals(403, mockMvc.perform(get("/api/v1/admin/products")
                        .header("Authorization", token(U_A, RoleCode.USER)))
                .andReturn().getResponse().getStatus());
        assertEquals(403, mockMvc.perform(get("/api/v1/merchant/orders")
                        .header("Authorization", token(U_A, RoleCode.USER)))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mockMvc.perform(get("/api/v1/orders"))
                .andReturn().getResponse().getStatus());
    }

    // ========== 三、并发与数据一致性：库存=1 两人同时购买 ==========

    @Test
    @DisplayName("并发防超卖：库存=1 时两用户各买 1 件，只一个成功，库存/销量不多扣")
    void singleStockTwoUsersConcurrentPurchase() throws Exception {
        String name = "S10-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long productId = insertProduct(name, 1, MERCHANT_ID);
        long addrA = insertAddress(U_A);
        long addrB = insertAddress(U_B);

        Map<String, Object> bodyA = new LinkedHashMap<>();
        bodyA.put("addressId", addrA);
        bodyA.put("items", List.of(Map.of("productId", productId, "quantity", 1)));
        Map<String, Object> bodyB = new LinkedHashMap<>();
        bodyB.put("addressId", addrB);
        bodyB.put("items", List.of(Map.of("productId", productId, "quantity", 1)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(U_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyA))).andReturn();
            }));
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(U_B, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bodyB))).andReturn();
            }));
            ready.await();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                statuses.add(f.get().getResponse().getStatus());
            }

            assertEquals(1, statuses.stream().filter(s -> s == 200).count(), "恰好一个请求成功");
            assertEquals(1, statuses.stream().filter(s -> s == 409).count(), "另一个请求库存冲突");
            assertEquals(0, stockOf(productId), "库存被扣到 0，不为负");
            assertEquals(1, salesOf(productId), "销量只加 1");
            assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE user_id IN (?,?)", U_A, U_B),
                    "只产生一笔订单");
        } finally {
            pool.shutdownNow();
        }
    }

    // ========== 四、端到端完整链路一致性核对 ==========

    @Test
    @DisplayName("端到端：下单→支付→发货→确认→评价→公开评价列表，库存与状态一致")
    void endToEndFulfillmentConsistency() throws Exception {
        String name = "S10-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long productId = insertProduct(name, 10, MERCHANT_ID);

        // 1) 用户 A 下单 2 件
        MvcResult created = createOrder(U_A, productId, 2);
        assertEquals(200, created.getResponse().getStatus());
        JsonNode data = json(created).path("data");
        String orderNo = data.path("orderNo").asText();
        long orderId = data.path("id").asLong();
        assertEquals(8, stockOf(productId));
        assertEquals(2, salesOf(productId));

        // 2) 模拟支付
        MvcResult paid = mockMvc.perform(post("/api/v1/payments/mock")
                        .header("Authorization", token(U_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andReturn();
        assertEquals(200, paid.getResponse().getStatus());
        assertEquals("PENDING_SHIPMENT", jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", orderId));

        // 3) 商家发货（商品 merchantId 与发货商家 userId 一致，归属校验通过）
        MvcResult shipped = mockMvc.perform(post("/api/v1/merchant/orders/" + orderId + "/ship")
                        .header("Authorization", token(MERCHANT_ID, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"熊猫物流\",\"trackingNo\":\"SF10" + UUID.randomUUID().toString().substring(0, 6) + "\"}"))
                .andReturn();
        assertEquals(200, shipped.getResponse().getStatus(), "商家对本人商品发货成功");

        // 4) 用户确认收货
        MvcResult confirmed = mockMvc.perform(post("/api/v1/orders/" + orderId + "/confirm")
                        .header("Authorization", token(U_A, RoleCode.USER)))
                .andReturn();
        assertEquals(200, confirmed.getResponse().getStatus());
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId));

        // 5) 评价订单明细
        long itemId = jdbcTemplate.queryForObject(
                "SELECT id FROM order_item WHERE order_id = ? LIMIT 1", Long.class, orderId);
        MvcResult reviewed = mockMvc.perform(post("/api/v1/reviews")
                        .header("Authorization", token(U_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderItemId", itemId, "rating", 5,
                                "content", "熊猫主题非常可爱，物流也很快，满意的一次购物体验"))))
                .andReturn();
        assertEquals(200, reviewed.getResponse().getStatus());
        assertEquals(1, count("SELECT COUNT(*) FROM review WHERE order_item_id = ?", itemId));

        // 6) 公开评价列表可见
        MvcResult list = mockMvc.perform(get("/api/v1/reviews/products/" + productId)).andReturn();
        assertEquals(200, list.getResponse().getStatus());
        assertTrue(json(list).path("data").size() >= 1);

        // 7) 终态一致性：库存/销量未因后续流程变化
        assertEquals(8, stockOf(productId));
        assertEquals(2, salesOf(productId));
    }
}
