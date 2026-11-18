package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.address.entity.UserAddress;
import com.pandago.address.mapper.UserAddressMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 阶段 8 集成测试：商家发货、用户确认收货、商品评价、管理端权限与审核留痕。
 *
 * <p>不使用类级 @Transactional：状态流转、物流/评价写入与并发结果必须在真实提交后查库验证。
 * 每个用例使用专用 userId 与随机商品名，@AfterEach 清理所有测试数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage8FulfillmentTests {

    private static final long USER_A = 94101L;
    private static final long USER_B = 94102L;
    private static final long MERCHANT_A = 94201L;
    private static final long MERCHANT_B = 94202L;
    private static final long ADMIN = 94301L;
    private static final String MAIN_IMAGE = "/images/panda-product.svg";
    private static final BigDecimal UNIT_PRICE = new BigDecimal("199.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ProductMapper productMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record Fixture(long orderId, String orderNo, long productId, long orderItemId,
                           long merchantId, String userToken, String merchantToken) {
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM review WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM shipment WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))",
                USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM payment_record WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))",
                USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM cart_item WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM user_address WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM operation_log WHERE operator_id IN (?,?,?,?,?)",
                USER_A, USER_B, MERCHANT_A, MERCHANT_B, ADMIN);
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'S8-%'");
    }

    private String token(long userId, RoleCode role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "u-" + userId, List.of(role));
    }

    private long firstCategoryId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/categories")).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data").get(0).path("id").asLong();
    }

    private long insertProduct(String name, long merchantId) throws Exception {
        Product product = new Product();
        product.setCategoryId(firstCategoryId());
        product.setMerchantId(merchantId);
        product.setName(name);
        product.setMainImage(MAIN_IMAGE);
        product.setPrice(UNIT_PRICE);
        product.setStock(50);
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
        address.setReceiverName("阶段8收货人");
        address.setPhone("13900139000");
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDistrict("滨江区");
        address.setDetailAddress("熊猫路 8 号");
        address.setIsDefault(0);
        addressMapper.insert(address);
        return address.getId();
    }

    /** 创建订单 -> 支付 -> 进入待发货，返回完整 fixture */
    private Fixture paidOrder(long userId, long merchantId) throws Exception {
        String name = "S8-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long productId = insertProduct(name, merchantId);
        long addressId = insertAddress(userId);
        String userToken = token(userId, RoleCode.USER);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("items", List.of(Map.of("productId", productId, "quantity", 2)));
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
        JsonNode data = json(created).path("data");
        long orderId = data.path("id").asLong();
        String orderNo = data.path("orderNo").asText();

        mockMvc.perform(post("/api/v1/payments/mock")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andReturn();

        OrderItem item = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)).get(0);
        return new Fixture(orderId, orderNo, productId, item.getId(), merchantId,
                userToken, token(merchantId, RoleCode.MERCHANT));
    }

    private MvcResult ship(long orderId, String auth, String company, String trackingNo) throws Exception {
        Map<String, Object> body = Map.of("company", company, "trackingNo", trackingNo);
        return mockMvc.perform(post("/api/v1/merchant/orders/" + orderId + "/ship")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private MvcResult confirm(long orderId, String auth) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + orderId + "/confirm")
                        .header("Authorization", auth))
                .andReturn();
    }

    private MvcResult review(long orderItemId, String auth, int rating, String content) throws Exception {
        Map<String, Object> body = Map.of("orderItemId", orderItemId, "rating", rating, "content", content);
        return mockMvc.perform(post("/api/v1/reviews")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private int http(MvcResult result) { return result.getResponse().getStatus(); }

    private String statusOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private List<Integer> runConcurrently(int n, java.util.function.Supplier<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return task.get();
            }));
        }
        ready.await();
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> f : futures) results.add(f.get());
        pool.shutdownNow();
        return results;
    }

    // 1. 普通用户访问商家接口 -> 403
    @Test
    void normalUserCannotAccessMerchantApi() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        MvcResult result = mockMvc.perform(get("/api/v1/merchant/orders")
                        .header("Authorization", fixture.userToken()))
                .andReturn();
        assertEquals(403, http(result));
    }

    // 2. 商家操作其他商家订单 -> 403
    @Test
    void merchantCannotShipOthersOrder() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        String otherMerchant = token(MERCHANT_B, RoleCode.MERCHANT);
        MvcResult result = ship(fixture.orderId(), otherMerchant, "熊猫速递", "SF1234567890");
        assertEquals(403, http(result));
        assertEquals("PENDING_SHIPMENT", statusOf(fixture.orderId()));
        assertEquals(0, count("SELECT COUNT(*) FROM shipment WHERE order_id = ?", fixture.orderId()));
    }

    // 3. 未支付订单发货 -> 409（状态机拒绝）
    @Test
    void cannotShipUnpaidOrder() throws Exception {
        String name = "S8-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long productId = insertProduct(name, MERCHANT_A);
        long addressId = insertAddress(USER_A);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("items", List.of(Map.of("productId", productId, "quantity", 1)));
        MvcResult created = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
        long orderId = json(created).path("data").path("id").asLong();

        MvcResult result = ship(orderId, token(MERCHANT_A, RoleCode.MERCHANT), "熊猫速递", "SF1234567890");
        assertEquals(409, http(result));
        assertEquals("PENDING_PAYMENT", statusOf(orderId));
    }

    // 4. 正常发货 -> 200，状态待收货，物流1条，无轨迹字段
    @Test
    void shipSuccessfully() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        MvcResult result = ship(fixture.orderId(), fixture.merchantToken(), "熊猫速递", "SF1234567890");
        assertEquals(200, http(result));
        JsonNode data = json(result).path("data");
        assertEquals("熊猫速递", data.path("company").asText());
        assertEquals("SF1234567890", data.path("trackingNo").asText());
        assertFalse(data.has("tracking"), "不应返回任何轨迹数据");
        assertFalse(data.path("shippedAt").isNull());
        assertEquals("PENDING_RECEIPT", statusOf(fixture.orderId()));
        assertEquals(1, count("SELECT COUNT(*) FROM shipment WHERE order_id = ?", fixture.orderId()));

        // 用户查询物流：只含公司与单号
        MvcResult mine = mockMvc.perform(get("/api/v1/orders/" + fixture.orderId() + "/shipment")
                        .header("Authorization", fixture.userToken()))
                .andReturn();
        assertEquals(200, http(mine));
        assertFalse(json(mine).path("data").has("tracking"));
    }

    // 5. 用户确认他人订单 -> 404
    @Test
    void userCannotConfirmOthersOrder() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        ship(fixture.orderId(), fixture.merchantToken(), "熊猫速递", "SF1234567890");
        MvcResult result = confirm(fixture.orderId(), token(USER_B, RoleCode.USER));
        assertEquals(404, http(result));
        assertEquals("PENDING_RECEIPT", statusOf(fixture.orderId()));
    }

    // 6. 待发货订单确认收货 -> 409
    @Test
    void cannotConfirmBeforeShipment() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        MvcResult result = confirm(fixture.orderId(), fixture.userToken());
        assertEquals(409, http(result));
        assertEquals("PENDING_SHIPMENT", statusOf(fixture.orderId()));
    }

    // 7. 正常确认收货 -> 200，COMPLETED，receivedAt
    @Test
    void confirmReceiptSuccessfully() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        ship(fixture.orderId(), fixture.merchantToken(), "熊猫速递", "SF1234567890");
        MvcResult result = confirm(fixture.orderId(), fixture.userToken());
        assertEquals(200, http(result));
        JsonNode data = json(result).path("data");
        assertEquals("COMPLETED", data.path("status").asText());
        assertFalse(data.path("receivedAt").isNull());
        assertEquals("COMPLETED", statusOf(fixture.orderId()));
        Object received = jdbcTemplate.queryForObject(
                "SELECT received_at FROM orders WHERE id = ?", Object.class, fixture.orderId());
        assertNotNull(received, "确认收货时间应写入数据库");
    }

    // 8. 重复确认收货 -> 409，无副作用
    @Test
    void duplicateConfirmRejected() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        ship(fixture.orderId(), fixture.merchantToken(), "熊猫速递", "SF1234567890");
        assertEquals(200, http(confirm(fixture.orderId(), fixture.userToken())));
        MvcResult again = confirm(fixture.orderId(), fixture.userToken());
        assertEquals(409, http(again));
        assertEquals("COMPLETED", statusOf(fixture.orderId()));
    }

    // 9. 未完成订单评价 -> 409
    @Test
    void cannotReviewIncompleteOrder() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        MvcResult result = review(fixture.orderItemId(), fixture.userToken(), 5, "商品质量很好，值得购买");
        assertEquals(409, http(result));
        assertEquals(0, count("SELECT COUNT(*) FROM review WHERE order_item_id = ?", fixture.orderItemId()));
    }

    // 10. 重复评价 -> 409（唯一索引 + 业务校验）
    @Test
    void duplicateReviewRejected() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        ship(fixture.orderId(), fixture.merchantToken(), "熊猫速递", "SF1234567890");
        confirm(fixture.orderId(), fixture.userToken());
        assertEquals(200, http(review(fixture.orderItemId(), fixture.userToken(), 5, "商品质量很好，值得购买")));
        MvcResult again = review(fixture.orderItemId(), fixture.userToken(), 4, "再评价一次内容");
        assertEquals(409, http(again));
        assertEquals(1, count("SELECT COUNT(*) FROM review WHERE order_item_id = ?", fixture.orderItemId()));
    }

    // 11. 商家自行审核商品 -> 403
    @Test
    void merchantCannotAuditProduct() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        String body = "{\"auditStatus\":\"APPROVED\"}";
        MvcResult result = mockMvc.perform(put("/api/v1/admin/products/" + fixture.productId() + "/audit")
                        .header("Authorization", fixture.merchantToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertEquals(403, http(result));
    }

    // 12. 普通用户访问管理员接口 -> 403
    @Test
    void normalUserCannotAccessAdminApi() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        assertEquals(403, http(mockMvc.perform(get("/api/v1/admin/users")
                .header("Authorization", fixture.userToken())).andReturn()));
        assertEquals(403, http(mockMvc.perform(get("/api/v1/admin/orders")
                .header("Authorization", fixture.userToken())).andReturn()));
    }

    // 13. 管理员审核流程和状态变更 -> 200，记录审核人/时间/意见与操作日志
    @Test
    void adminAuditRecordsReviewerAndLog() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        // 先置为待审核
        jdbcTemplate.update("UPDATE product SET audit_status = 'PENDING', status = 'OFF_SALE' WHERE id = ?",
                fixture.productId());
        String adminToken = token(ADMIN, RoleCode.ADMIN);
        String body = "{\"auditStatus\":\"REJECTED\",\"reason\":\"图片不清晰\"}";
        MvcResult result = mockMvc.perform(put("/api/v1/admin/products/" + fixture.productId() + "/audit")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        assertEquals(200, http(result));
        assertEquals("REJECTED", jdbcTemplate.queryForObject(
                "SELECT audit_status FROM product WHERE id = ?", String.class, fixture.productId()));
        assertEquals("OFF_SALE", jdbcTemplate.queryForObject(
                "SELECT status FROM product WHERE id = ?", String.class, fixture.productId()));
        assertEquals("图片不清晰", jdbcTemplate.queryForObject(
                "SELECT audit_reason FROM product WHERE id = ?", String.class, fixture.productId()));
        Object auditBy = jdbcTemplate.queryForObject(
                "SELECT audit_by FROM product WHERE id = ?", Object.class, fixture.productId());
        assertNotNull(auditBy);
        assertEquals(ADMIN, ((Number) auditBy).longValue());
        Object auditTime = jdbcTemplate.queryForObject(
                "SELECT audit_time FROM product WHERE id = ?", Object.class, fixture.productId());
        assertNotNull(auditTime, "审核时间应写入");
        assertEquals(1, count("SELECT COUNT(*) FROM operation_log WHERE module='PRODUCT' AND operation='AUDIT' AND target_id = ?",
                fixture.productId()));
    }

    // 补充：完整链路 发货->确认->评价 与 商品评价列表公开可见
    @Test
    void fullFulfillmentAndPublicReviewList() throws Exception {
        Fixture fixture = paidOrder(USER_A, MERCHANT_A);
        assertEquals(200, http(ship(fixture.orderId(), fixture.merchantToken(), "熊猫速递", "SF1234567890")));
        assertEquals(200, http(confirm(fixture.orderId(), fixture.userToken())));
        assertEquals(200, http(review(fixture.orderItemId(), fixture.userToken(), 5, "非常满意，会再次购买")));

        MvcResult list = mockMvc.perform(get("/api/v1/reviews/products/" + fixture.productId())).andReturn();
        assertEquals(200, http(list));
        JsonNode arr = json(list).path("data");
        assertEquals(1, arr.size());
        assertEquals("非常满意，会再次购买", arr.get(0).path("content").asText());
        assertEquals(5, arr.get(0).path("rating").asInt());
    }

    // 补充：商家工作台订单列表只返回本人订单
    @Test
    void merchantOrderListScoped() throws Exception {
        Fixture mine = paidOrder(USER_A, MERCHANT_A);
        Fixture others = paidOrder(USER_B, MERCHANT_B);
        MvcResult result = mockMvc.perform(get("/api/v1/merchant/orders")
                        .header("Authorization", mine.merchantToken())
                        .param("pageNum", "1").param("pageSize", "10"))
                .andReturn();
        assertEquals(200, http(result));
        JsonNode data = json(result).path("data");
        assertEquals(1, data.path("total").asInt());
        assertEquals(mine.orderId(), data.path("list").get(0).path("id").asLong());
        assertNotEquals(others.orderId(), data.path("list").get(0).path("id").asLong());
    }

    private static void assertNotEquals(long a, long b) {
        org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    }
}
