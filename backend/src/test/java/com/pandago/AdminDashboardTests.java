package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.enums.RoleCode;
import com.pandago.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台管理工作台专项测试。
 *
 * <p>不依赖静态假数据：测试临时插入用户/商品/订单/支付记录，验证聚合口径、
 * 最近列表排序与 LIMIT、买家信息脱敏、角色权限，并在结束后完整清理。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardTests {

    private static final long ADMIN_ID = 99001L;
    private static final long USER_ID_A = 9700301L;
    private static final long USER_ID_B = 9700302L;
    private static final long ORDER_ID_BASE = 9700400L;
    private static final long PRODUCT_ID_BASE = 9700500L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_record WHERE order_id BETWEEN ? AND ?",
                ORDER_ID_BASE + 1, ORDER_ID_BASE + 10);
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id BETWEEN ? AND ?",
                ORDER_ID_BASE + 1, ORDER_ID_BASE + 10);
        jdbcTemplate.update("DELETE FROM orders WHERE id BETWEEN ? AND ?",
                ORDER_ID_BASE + 1, ORDER_ID_BASE + 10);
        jdbcTemplate.update("DELETE FROM product WHERE id BETWEEN ? AND ?",
                PRODUCT_ID_BASE + 1, PRODUCT_ID_BASE + 10);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?)", USER_ID_A, USER_ID_B);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?)", USER_ID_A, USER_ID_B);
    }

    private String token(long userId, RoleCode role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "dash-" + userId, List.of(role));
    }

    private JsonNode dashboard() throws Exception {
        return dashboard(token(ADMIN_ID, RoleCode.ADMIN));
    }

    private JsonNode dashboard(String authorization) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private void insertBuyer(long id, String username) {
        jdbcTemplate.update("INSERT INTO user(id, username, password_hash, nickname, status, deleted) "
                        + "VALUES (?,?,?,?,1,0)",
                id, username, "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu", "工作台测试");
        jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code='USER'", id);
    }

    private void insertOrder(long id, String status, String amount, boolean paid, int minutesAgo) {
        String orderNo = "ADBD-" + id;
        jdbcTemplate.update("INSERT INTO orders(id, order_no, user_id, merchant_id, total_amount, status, "
                        + "receiver_name, receiver_phone, receiver_province, receiver_city, receiver_district, "
                        + "receiver_detail_address, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,"
                        + "DATE_ADD(NOW(), INTERVAL ? MINUTE))",
                id, orderNo, USER_ID_A, 0L, new java.math.BigDecimal(amount), status,
                "测试买家", "13812345678", "广东省", "深圳市", "南山区", "测试地址", minutesAgo);
        jdbcTemplate.update("INSERT INTO order_item(order_id, product_id, product_name, product_image, "
                        + "unit_price, quantity, subtotal) VALUES (?,?,?,?,?,?,?)",
                id, 1L, "工作台测试商品-" + id, "/images/panda-product.svg",
                new java.math.BigDecimal(amount), 1, new java.math.BigDecimal(amount));
        if (paid) {
            jdbcTemplate.update("INSERT INTO payment_record(payment_no, order_id, order_no, user_id, "
                            + "pay_amount, payment_method, status, paid_at) VALUES (?,?,?,?,?,?,?,NOW())",
                    "ADBD-PAY-" + id, id, orderNo, USER_ID_A,
                    new java.math.BigDecimal(amount), "MOCK", "SUCCESS");
        }
    }

    private void insertProduct(long id, String name, String status, String auditStatus, int minutesAgo) {
        jdbcTemplate.update("INSERT INTO product(id, category_id, merchant_id, name, main_image, price, "
                        + "stock, status, audit_status, sort, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,"
                        + "DATE_ADD(NOW(), INTERVAL ? MINUTE))",
                id, 1L, 0L, name, "/images/panda-product.svg", new java.math.BigDecimal("59.00"),
                10, status, auditStatus, 0, minutesAgo);
    }

    // ---------- 权限 ----------

    @Test
    void adminCanAccessDashboardAndAllFieldsArePresent() throws Exception {
        JsonNode data = dashboard();
        for (String key : new String[]{"userTotal", "accountTotal", "todayNewUsers", "productTotal",
                "productOnSale", "pendingReviewProducts", "orderTotal", "pendingShipmentOrders",
                "completedOrders", "todayNewOrders", "totalSalesAmount", "recentOrders",
                "pendingReviewProductList"}) {
            assertFalse(data.path(key).isMissingNode(), "缺少字段 " + key);
        }
        assertTrue(data.path("accountTotal").asLong() > 0);
        assertTrue(data.path("productTotal").asLong() > 0);
        assertTrue(data.path("orderTotal").asLong() > 0);
    }

    @Test
    void normalUserCannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", token(USER_ID_A, RoleCode.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void merchantCannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", token(USER_ID_A, RoleCode.MERCHANT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void queryParametersAreIgnored() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/dashboard")
                        .param("userTotal", "999999")
                        .param("adminId", "1")
                        .header("Authorization", token(ADMIN_ID, RoleCode.ADMIN)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        assertTrue(data.path("userTotal").asLong() < 999999);
    }

    // ---------- 统计口径 ----------

    @Test
    void userStatsCountBuyersByDistinctRoleAndTodayNewUsers() throws Exception {
        JsonNode before = dashboard();
        insertBuyer(USER_ID_A, "dash_buyer_a");
        insertBuyer(USER_ID_B, "dash_buyer_b");
        JsonNode after = dashboard();

        assertEquals(before.path("userTotal").asLong() + 2, after.path("userTotal").asLong());
        assertEquals(before.path("accountTotal").asLong() + 2, after.path("accountTotal").asLong());
        assertEquals(before.path("todayNewUsers").asLong() + 2, after.path("todayNewUsers").asLong());
    }

    @Test
    void productStatsSeparateTotalOnSaleAndPending() throws Exception {
        JsonNode before = dashboard();
        insertProduct(PRODUCT_ID_BASE + 1, "ADBD-上架", "ON_SALE", "APPROVED", 1);
        insertProduct(PRODUCT_ID_BASE + 2, "ADBD-待审", "OFF_SALE", "PENDING", 2);
        insertProduct(PRODUCT_ID_BASE + 3, "ADBD-驳回", "OFF_SALE", "REJECTED", 3);
        JsonNode after = dashboard();

        assertEquals(before.path("productTotal").asLong() + 3, after.path("productTotal").asLong());
        assertEquals(before.path("productOnSale").asLong() + 1, after.path("productOnSale").asLong());
        assertEquals(before.path("pendingReviewProducts").asLong() + 1,
                after.path("pendingReviewProducts").asLong());
    }

    @Test
    void orderStatsExcludeUnpaidAndCancelledFromSalesAmount() throws Exception {
        JsonNode before = dashboard();
        insertOrder(ORDER_ID_BASE + 1, "PENDING_SHIPMENT", "100.00", true, 5);
        insertOrder(ORDER_ID_BASE + 2, "PENDING_RECEIPT", "50.00", true, 6);
        insertOrder(ORDER_ID_BASE + 3, "COMPLETED", "20.00", true, 7);
        // 已取消：即使有支付记录也不应计入交易额
        insertOrder(ORDER_ID_BASE + 4, "CANCELLED", "999.00", true, 8);
        // 未支付：不应计入交易额
        insertOrder(ORDER_ID_BASE + 5, "PENDING_PAYMENT", "888.00", false, 9);
        JsonNode after = dashboard();

        assertEquals(before.path("orderTotal").asLong() + 5, after.path("orderTotal").asLong());
        assertEquals(before.path("pendingShipmentOrders").asLong() + 1,
                after.path("pendingShipmentOrders").asLong());
        assertEquals(before.path("completedOrders").asLong() + 1, after.path("completedOrders").asLong());
        assertEquals(before.path("todayNewOrders").asLong() + 5, after.path("todayNewOrders").asLong());
        assertEquals(0, before.path("totalSalesAmount").decimalValue()
                        .add(new java.math.BigDecimal("170.00"))
                        .compareTo(after.path("totalSalesAmount").decimalValue()),
                "交易金额应只增加已支付且未取消的 170.00");
    }

    // ---------- 最近列表 ----------

    @Test
    void recentOrdersAreDescendingLimitedAndMasked() throws Exception {
        for (int i = 1; i <= 6; i++) {
            insertOrder(ORDER_ID_BASE + i, "COMPLETED", "10.00", true, 10 + i);
        }
        JsonNode list = dashboard().path("recentOrders");
        assertEquals(5, list.size(), "最近订单最多返回 5 条");
        assertEquals("ADBD-" + (ORDER_ID_BASE + 6), list.get(0).path("orderNo").asText(),
                "应按创建时间倒序，最新订单排在最前");
        for (JsonNode row : list) {
            assertFalse(("ADBD-" + (ORDER_ID_BASE + 1)).equals(row.path("orderNo").asText()),
                    "最旧的一条测试订单应被 LIMIT 排除");
            assertEquals("138****5678", row.path("buyerPhoneMasked").asText());
            assertFalse(row.path("buyerPhoneMasked").asText().contains("12345678"));
            assertTrue(row.path("firstProductName").asText().startsWith("工作台测试商品"));
            assertEquals("已完成", row.path("statusDescription").asText());
        }
    }

    @Test
    void pendingProductListOnlyContainsPendingNewestFirst() throws Exception {
        for (int i = 1; i <= 6; i++) {
            insertProduct(PRODUCT_ID_BASE + i, "ADBD-待审-" + i, "OFF_SALE", "PENDING", 20 + i);
        }
        JsonNode list = dashboard().path("pendingReviewProductList");
        assertEquals(5, list.size(), "待审核商品最多返回 5 条");
        assertEquals("ADBD-待审-6", list.get(0).path("name").asText());
        for (JsonNode row : list) {
            assertEquals("PENDING", row.path("auditStatus").asText());
            assertEquals("平台自营", row.path("merchantName").asText());
        }
    }

    @Test
    void emptyListsAreReturnedAsArrays() throws Exception {
        JsonNode data = dashboard();
        assertTrue(data.path("recentOrders").isArray());
        assertTrue(data.path("pendingReviewProductList").isArray());
    }
}
