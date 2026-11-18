package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.address.entity.UserAddress;
import com.pandago.address.mapper.UserAddressMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.payment.support.PaymentFaultHook;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 7 集成测试：订单状态机、模拟支付、幂等、取消与库存回补。
 *
 * <p>不使用类级 @Transactional：事务回滚与并发结果必须在真实提交后查库验证。
 * 每个用例使用专用 userId 与随机商品名，@AfterEach 清理所有测试数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage7OrderStatusTests {

    private static final long USER_A = 93001L;
    private static final long USER_B = 93002L;
    private static final String MAIN_IMAGE = "/images/panda-product.svg";
    private static final BigDecimal UNIT_PRICE = new BigDecimal("199.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ProductMapper productMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 支付事务接缝：默认空实现；回滚用例让 afterPaymentInsert 抛异常 */
    @MockBean private PaymentFaultHook paymentFaultHook;

    private record OrderFixture(long orderId, String orderNo, long productId,
                                int initialStock, int stockAfterCreate, String token) {
    }

    @BeforeEach
    void resetHook() {
        Mockito.reset(paymentFaultHook);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_record WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))",
                USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM cart_item WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM user_address WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'S7-%'");
    }

    private String token(long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId, List.of(RoleCode.USER));
    }

    private long firstCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private long insertProduct(String name, int stock) throws Exception {
        Product product = new Product();
        product.setCategoryId(firstCategoryId());
        product.setMerchantId(0L);
        product.setName(name);
        product.setMainImage(MAIN_IMAGE);
        product.setPrice(UNIT_PRICE);
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
        address.setReceiverName("阶段7收货人");
        address.setPhone("13900139000");
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDistrict("滨江区");
        address.setDetailAddress("熊猫路 7 号");
        address.setIsDefault(0);
        addressMapper.insert(address);
        return address.getId();
    }

    /** 通过 HTTP 真实创建待支付订单，返回订单与库存基线。 */
    private OrderFixture createOrder(long userId, int quantity) throws Exception {
        String name = "S7-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long productId = insertProduct(name, 20);
        long addressId = insertAddress(userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("items", List.of(Map.of("productId", productId, "quantity", quantity)));
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = json(result).path("data");
        return new OrderFixture(created.path("id").asLong(), created.path("orderNo").asText(),
                productId, 20, 20 - quantity, token(userId));
    }

    private OrderFixture createOrder(long userId) throws Exception {
        return createOrder(userId, 2);
    }

    private MvcResult pay(String orderNo, String authHeader) throws Exception {
        return pay(orderNo, authHeader, Map.of());
    }

    /** extraFields 用于验证前端夹带金额会被忽略。 */
    private MvcResult pay(String orderNo, String authHeader, Map<String, Object> extraFields) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderNo", orderNo);
        body.putAll(extraFields);
        return mockMvc.perform(post("/api/v1/payments/mock")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private MvcResult cancel(long orderId, String authHeader) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", authHeader))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private int stockOf(long productId) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        return stock == null ? -1 : stock;
    }

    private String statusOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private int httpStatus(MvcResult result) {
        return result.getResponse().getStatus();
    }

    /** 1. 正常支付：服务端金额为准，状态变为待发货，产生支付流水号与时间。 */
    @Test
    void paySuccessfullyWithServerSideAmount() throws Exception {
        OrderFixture fixture = createOrder(USER_A);

        MvcResult result = pay(fixture.orderNo(), fixture.token(),
                Map.of("amount", 0.01, "payAmount", 0.01, "totalAmount", 0.01));
        assertEquals(200, httpStatus(result));
        JsonNode data = json(result).path("data");
        assertTrue(data.path("paid").asBoolean());
        assertTrue(data.path("paymentNo").asText().startsWith("PAY"));
        assertEquals(398.00, data.path("payAmount").asDouble(), 0.001);
        assertEquals("PENDING_SHIPMENT", data.path("orderStatus").asText());
        assertFalse(data.path("paidAt").isNull());

        assertEquals(1, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", fixture.orderId()));
        assertEquals(398.00,
                jdbcTemplate.queryForObject("SELECT pay_amount FROM payment_record WHERE order_id = ?", BigDecimal.class,
                        fixture.orderId()).doubleValue(), 0.001);
        assertEquals("PENDING_SHIPMENT", statusOf(fixture.orderId()));
        assertEquals(fixture.stockAfterCreate(), stockOf(fixture.productId()), "支付不应再次改变库存");
    }

    /** 2. 重复支付：返回明确 409，不重复产生支付记录或改状态。 */
    @Test
    void duplicatePaymentRejected() throws Exception {
        OrderFixture fixture = createOrder(USER_A);
        assertEquals(200, httpStatus(pay(fixture.orderNo(), fixture.token())));

        MvcResult again = pay(fixture.orderNo(), fixture.token());
        assertEquals(409, httpStatus(again));
        assertEquals(409, json(again).path("code").asInt());
        assertTrue(json(again).path("message").asText().contains("已支付"));

        assertEquals(1, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", fixture.orderId()));
        assertEquals("PENDING_SHIPMENT", statusOf(fixture.orderId()));
    }

    /** 3. 两个并发支付请求：只能一个成功。 */
    @Test
    void concurrentPaymentsOnlyOneSucceeds() throws Exception {
        OrderFixture fixture = createOrder(USER_A);
        List<Integer> statuses = runConcurrently(2, () -> httpStatus(pay(fixture.orderNo(), fixture.token())));

        assertEquals(1, statuses.stream().filter(s -> s == 200).count());
        assertEquals(1, statuses.stream().filter(s -> s == 409).count());
        assertEquals(1, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", fixture.orderId()));
        assertEquals("PENDING_SHIPMENT", statusOf(fixture.orderId()));
    }

    /** 4. 支付已取消订单：状态机拒绝，不产生支付记录。 */
    @Test
    void payCancelledOrderRejected() throws Exception {
        OrderFixture fixture = createOrder(USER_A);
        assertEquals(200, httpStatus(cancel(fixture.orderId(), fixture.token())));

        MvcResult result = pay(fixture.orderNo(), fixture.token());
        assertEquals(409, httpStatus(result));
        assertEquals("CANCELLED", statusOf(fixture.orderId()));
        assertEquals(0, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", fixture.orderId()));
    }

    /** 5. 支付其他用户订单：归属校验返回 404。 */
    @Test
    void payOtherUsersOrderRejected() throws Exception {
        OrderFixture fixture = createOrder(USER_A);

        MvcResult result = pay(fixture.orderNo(), token(USER_B));
        assertEquals(404, httpStatus(result));
        assertEquals("PENDING_PAYMENT", statusOf(fixture.orderId()));
        assertEquals(0, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", fixture.orderId()));
    }

    /** 6. 正常取消待支付订单：状态变为已取消，库存恢复。 */
    @Test
    void cancelPendingOrderRestoresStock() throws Exception {
        OrderFixture fixture = createOrder(USER_A, 3);
        assertEquals(fixture.initialStock() - 3, stockOf(fixture.productId()));

        MvcResult result = cancel(fixture.orderId(), fixture.token());
        assertEquals(200, httpStatus(result));
        assertEquals("CANCELLED", json(result).path("data").path("status").asText());
        assertEquals(fixture.initialStock(), stockOf(fixture.productId()));
        assertEquals("CANCELLED", statusOf(fixture.orderId()));
    }

    /** 7. 重复取消：第二次返回 409，库存不重复增加。 */
    @Test
    void repeatedCancelDoesNotRestoreStockTwice() throws Exception {
        OrderFixture fixture = createOrder(USER_A, 4);
        assertEquals(200, httpStatus(cancel(fixture.orderId(), fixture.token())));
        int stockAfterCancel = stockOf(fixture.productId());

        MvcResult again = cancel(fixture.orderId(), fixture.token());
        assertEquals(409, httpStatus(again));
        assertTrue(json(again).path("message").asText().contains("不允许"));
        assertEquals(stockAfterCancel, stockOf(fixture.productId()));
    }

    /** 8. 并发取消：只有一个请求获得状态变更权。 */
    @Test
    void concurrentCancelsOnlyOneSucceeds() throws Exception {
        OrderFixture fixture = createOrder(USER_A, 2);
        List<Integer> statuses = runConcurrently(2, () -> httpStatus(cancel(fixture.orderId(), fixture.token())));

        assertEquals(1, statuses.stream().filter(s -> s == 200).count());
        assertEquals(1, statuses.stream().filter(s -> s == 409).count());
        assertEquals(fixture.initialStock(), stockOf(fixture.productId()));
        assertEquals("CANCELLED", statusOf(fixture.orderId()));
    }

    /** 9. 连续多次取消后库存只恢复一次（明确验证最终库存值）。 */
    @Test
    void stockRestoredExactlyOnceAfterMultipleCancels() throws Exception {
        OrderFixture fixture = createOrder(USER_A, 5);
        assertEquals(fixture.initialStock() - 5, stockOf(fixture.productId()));

        assertEquals(200, httpStatus(cancel(fixture.orderId(), fixture.token())));
        assertEquals(409, httpStatus(cancel(fixture.orderId(), fixture.token())));
        assertEquals(409, httpStatus(cancel(fixture.orderId(), fixture.token())));

        assertEquals(fixture.initialStock(), stockOf(fixture.productId()));
        assertEquals(1, count("SELECT COUNT(*) FROM product WHERE id = ? AND stock = ?",
                fixture.productId(), fixture.initialStock()));
    }

    /** 10. 已支付订单不能通过普通取消接口取消。 */
    @Test
    void paidOrderCannotBeCancelledByUser() throws Exception {
        OrderFixture fixture = createOrder(USER_A);
        assertEquals(200, httpStatus(pay(fixture.orderNo(), fixture.token())));
        int stockAfterPay = stockOf(fixture.productId());

        MvcResult result = cancel(fixture.orderId(), fixture.token());
        assertEquals(409, httpStatus(result));
        assertEquals("PENDING_SHIPMENT", statusOf(fixture.orderId()));
        assertEquals(stockAfterPay, stockOf(fixture.productId()));
        assertEquals(1, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", fixture.orderId()));
    }

    /** 11. 支付记录写入后订单更新失败：事务回滚，不留支付记录。 */
    @Test
    void paymentRollsBackWhenOrderUpdateFails() throws Exception {
        OrderFixture fixture = createOrder(USER_A);
        Mockito.doThrow(new RuntimeException("模拟支付记录写入后失败"))
                .when(paymentFaultHook).afterPaymentInsert();

        MvcResult result = pay(fixture.orderNo(), fixture.token());
        assertEquals(500, httpStatus(result));

        assertEquals(0, count("SELECT COUNT(*) FROM payment_record WHERE order_id = ?", fixture.orderId()));
        assertEquals("PENDING_PAYMENT", statusOf(fixture.orderId()));
        assertEquals(fixture.stockAfterCreate(), stockOf(fixture.productId()));
    }

    /** 补充：状态查询与支付结果查询都必须校验用户归属。 */
    @Test
    void statusAndPaymentQueriesRequireOwnership() throws Exception {
        OrderFixture fixture = createOrder(USER_A);

        MvcResult ownStatus = mockMvc.perform(get("/api/v1/orders/" + fixture.orderId() + "/status")
                        .header("Authorization", fixture.token()))
                .andReturn();
        assertEquals(200, httpStatus(ownStatus));
        JsonNode statusData = json(ownStatus).path("data");
        assertTrue(statusData.path("canPay").asBoolean());
        assertTrue(statusData.path("canCancel").asBoolean());

        MvcResult othersStatus = mockMvc.perform(get("/api/v1/orders/" + fixture.orderId() + "/status")
                        .header("Authorization", token(USER_B)))
                .andReturn();
        MvcResult othersPayment = mockMvc.perform(get("/api/v1/payments/orders/" + fixture.orderNo())
                        .header("Authorization", token(USER_B)))
                .andReturn();
        assertEquals(404, httpStatus(othersStatus));
        assertEquals(404, httpStatus(othersPayment));

        assertEquals(200, httpStatus(pay(fixture.orderNo(), fixture.token())));
        MvcResult paidStatus = mockMvc.perform(get("/api/v1/orders/" + fixture.orderId() + "/status")
                        .header("Authorization", fixture.token()))
                .andReturn();
        assertFalse(json(paidStatus).path("data").path("canPay").asBoolean());
        assertFalse(json(paidStatus).path("data").path("canCancel").asBoolean());

        MvcResult paymentResult = mockMvc.perform(get("/api/v1/payments/orders/" + fixture.orderNo())
                        .header("Authorization", fixture.token()))
                .andReturn();
        assertEquals(200, httpStatus(paymentResult));
        assertTrue(json(paymentResult).path("data").path("paid").asBoolean());
    }

    /** 并发执行同一动作，用 CountDownLatch 尽量同时发起请求。 */
    private List<Integer> runConcurrently(int threads, ThrowingSupplier<Integer> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.get();
                }));
            }
            ready.await();
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
