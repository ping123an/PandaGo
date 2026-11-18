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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商家工作台与商家资料验收测试（V9）。
 *
 * <p>覆盖三大类：
 * 1. 权限：USER / ADMIN / 未登录 访问商家接口被拒，merchantId 不接受前端传值；
 * 2. 业务：工作台统计口径（上架数、待发货、已完成、已支付销售额排除待支付/取消）、
 *    最近订单手机号脱敏、商家隔离；
 * 3. 资料：查询、白名单更新、空店铺名/非法手机号校验、敏感字段篡改无效、懒初始化。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchantWorkspaceTests {

    /** 两个独立商家 + 一个普通用户，全部使用高位 ID 避免与演示数据冲突 */
    private static final long MERCHANT_A = 9600101L;
    private static final long MERCHANT_B = 9600102L;
    private static final long NORMAL_USER = 9600103L;
    private static final String IMG = "/images/panda-product.svg";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertUser(MERCHANT_A, "mw_merchant_a", "熊猫甲店铺");
        insertUser(MERCHANT_B, "mw_merchant_b", "熊猫乙店铺");
        insertUser(NORMAL_USER, "mw_user", "普通用户");
        insertProfile(MERCHANT_A, "甲店铺原始名");
        insertProfile(MERCHANT_B, "乙店铺原始名");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM merchant_profile WHERE user_id IN (?,?,?)", MERCHANT_A, MERCHANT_B, NORMAL_USER);
        jdbcTemplate.update("DELETE FROM orders WHERE merchant_id IN (?,?)", MERCHANT_A, MERCHANT_B);
        jdbcTemplate.update("DELETE FROM product WHERE merchant_id IN (?,?)", MERCHANT_A, MERCHANT_B);
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (?,?,?)", MERCHANT_A, MERCHANT_B, NORMAL_USER);
        jdbcTemplate.update("DELETE FROM user WHERE id IN (?,?,?)", MERCHANT_A, MERCHANT_B, NORMAL_USER);
    }

    // ---------------- helpers ----------------

    private void insertUser(long id, String username, String nickname) {
        jdbcTemplate.update("INSERT INTO user(id, username, password_hash, nickname, status, deleted) VALUES (?,?,?,?,1,0)",
                id, username, "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu", nickname);
    }

    private void insertProfile(long userId, String storeName) {
        jdbcTemplate.update("INSERT INTO merchant_profile(user_id, store_name, status, audit_status) VALUES (?,?,'NORMAL','APPROVED')",
                userId, storeName);
    }

    private long categoryId() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/categories")).andReturn();
        return jsonObj(r).path("data").get(0).path("id").asLong();
    }

    /** 直接插入商品，可指定上下架 / 审核状态 */
    private long product(long merchantId, String name, String price, int stock, String status, String audit) throws Exception {
        long id = nextProductId();
        jdbcTemplate.update("INSERT INTO product(id, category_id, merchant_id, name, main_image, price, stock, sales, status, audit_status, sort, deleted) "
                        + "VALUES (?,?,?,?,?,?,?,0,?,?,0,0)",
                id, categoryId(), merchantId, name, IMG, new BigDecimal(price), stock, status, audit);
        return id;
    }

    private long nextProductId() {
        Long max = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id),0) FROM product", Long.class);
        return (max == null ? 0 : max) + 1000 + (long) (Math.random() * 900);
    }

    /** 直接插入订单 snapshot，用于统计口径验证 */
    private void order(long merchantId, String status, String amount) {
        jdbcTemplate.update("INSERT INTO orders(order_no, user_id, merchant_id, source, total_amount, status, "
                        + "receiver_name, receiver_phone, receiver_province, receiver_city, receiver_district, receiver_detail_address) "
                        + "VALUES (?,?,?,'CART',?,?,'买家','13800138000','浙江省','杭州市','西湖区','熊猫路1号')",
                "MW" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                NORMAL_USER, merchantId, new BigDecimal(amount), status);
    }

    private String token(long userId, RoleCode role) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "u-" + userId, List.of(role));
    }

    private JsonNode jsonObj(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsByteArray());
    }

    private String profileBody(String storeName, String phone) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storeName", storeName);
        m.put("description", "测试简介");
        m.put("contactName", "联系人");
        m.put("contactPhone", phone);
        m.put("address", "测试地址");
        return objectMapper.writeValueAsString(m);
    }

    // ---------------- 权限测试 ----------------

    @Test
    void dashboard_merchantRole_ok_readsOwnStoreName() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/merchant/dashboard")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");
        assertEquals("甲店铺原始名", data.path("storeName").asText());
        assertEquals("NORMAL", data.path("storeStatus").asText());
    }

    @Test
    void dashboard_normalUser_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/dashboard")
                        .header("Authorization", token(NORMAL_USER, RoleCode.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_admin_forbidden_serviceLayer() throws Exception {
        // URL 规则允许 ADMIN，但类级 @PreAuthorize 要求 MERCHANT，Service 再校验一次
        mockMvc.perform(get("/api/v1/merchant/dashboard")
                        .header("Authorization", token(NORMAL_USER, RoleCode.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboard_noToken_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profile_normalUser_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", token(NORMAL_USER, RoleCode.USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(NORMAL_USER, RoleCode.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("篡改名", "13800138000")))
                .andExpect(status().isForbidden());
    }

    // ---------------- 统计口径测试 ----------------

    @Test
    void dashboard_statsCountOnlyOwnMerchant_andSalesExcludeUnpaidAndCancelled() throws Exception {
        // 商家 A：2 商品（1 上架审核通过、1 上架待审核），商家 B：1 商品
        product(MERCHANT_A, "MW-A-ON", "10.00", 5, "ON_SALE", "APPROVED");
        product(MERCHANT_A, "MW-A-PENDING", "20.00", 5, "ON_SALE", "PENDING");
        product(MERCHANT_B, "MW-B-ON", "30.00", 5, "ON_SALE", "APPROVED");

        // 商家 A 订单：待发货 100、已完成 200、待支付 999（不计）、已取消 888（不计）
        order(MERCHANT_A, "PENDING_SHIPMENT", "100.00");
        order(MERCHANT_A, "COMPLETED", "200.00");
        order(MERCHANT_A, "PENDING_PAYMENT", "999.00");
        order(MERCHANT_A, "CANCELLED", "888.00");
        // 商家 B 订单：已完成 777，不能计入 A
        order(MERCHANT_B, "COMPLETED", "777.00");

        MvcResult r = mockMvc.perform(get("/api/v1/merchant/dashboard")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");

        assertEquals(2, data.path("productTotal").asLong());
        assertEquals(1, data.path("productOnSale").asLong(), "上架数只统计 ON_SALE + APPROVED");
        assertEquals(1, data.path("pendingShipmentOrders").asLong());
        assertEquals(1, data.path("completedOrders").asLong());
        assertEquals(0, new BigDecimal("300.00").compareTo(new BigDecimal(data.path("salesAmount").asText())),
                "销售额=100+200，排除待支付与取消，且不含商家B");
    }

    @Test
    void dashboard_recentOrder_masksBuyerPhone() throws Exception {
        order(MERCHANT_A, "PENDING_SHIPMENT", "50.00");
        MvcResult r = mockMvc.perform(get("/api/v1/merchant/dashboard")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");
        assertTrue(data.path("recentOrders").size() >= 1);
        String masked = data.path("recentOrders").get(0).path("buyerPhoneMasked").asText();
        assertEquals("138****8000", masked);
        assertNotEquals("13800138000", masked);
    }

    @Test
    void dashboard_zeroData_returnsEmptyListsAndZeroStats() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/merchant/dashboard")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");
        assertEquals(0, data.path("productTotal").asLong());
        assertEquals(0, data.path("salesAmount").asLong());
        assertEquals(0, data.path("recentOrders").size());
        assertEquals(0, data.path("recentProducts").size());
    }

    // ---------------- 资料测试 ----------------

    @Test
    void profile_get_returnsOwnProfile() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");
        assertEquals(MERCHANT_A, data.path("userId").asLong());
        assertEquals("甲店铺原始名", data.path("storeName").asText());
        assertEquals("正常经营", data.path("statusDescription").asText());
        assertEquals("审核通过", data.path("auditStatusDescription").asText());
    }

    @Test
    void profile_update_whitelistFields_only() throws Exception {
        String body = profileBody("甲店铺新名", "13912345678");
        MvcResult r = mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");
        assertEquals("甲店铺新名", data.path("storeName").asText());
        assertEquals("13912345678", data.path("contactPhone").asText());

        // 回读确认落库
        JsonNode fresh = jsonObj(mockMvc.perform(get("/api/v1/merchant/profile")
                .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))).andReturn()).path("data");
        assertEquals("甲店铺新名", fresh.path("storeName").asText());
    }

    @Test
    void profile_logo_setAndClear_usesExplicitNull() throws Exception {
        String logo = "/images/panda-product.svg";
        // 1. 设置店铺头像
        String setBody = "{\"storeName\":\"甲店铺原始名\",\"logo\":\"" + logo + "\"}";
        MvcResult setResult = mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON).content(setBody))
                .andExpect(status().isOk()).andReturn();
        assertEquals(logo, jsonObj(setResult).path("data").path("logo").asText());

        // 2. 用 null 清空头像（前端传 logo:null），MyBatis-Plus 必须显式 set null
        String clearBody = "{\"storeName\":\"甲店铺原始名\",\"logo\":null}";
        MvcResult clearResult = mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON).content(clearBody))
                .andExpect(status().isOk()).andReturn();
        JsonNode cleared = jsonObj(clearResult).path("data").path("logo");
        assertTrue(cleared.isMissingNode() || cleared.isNull(), "logo 应被清空，不能残留旧 URL");

        // 3. 回读数据库确认落库为 null
        String dbLogo = jdbcTemplate.queryForObject("SELECT logo FROM merchant_profile WHERE user_id=?", String.class, MERCHANT_A);
        assertTrue(dbLogo == null || dbLogo.isEmpty(), "数据库 logo 应为空，实际=" + dbLogo);
    }

    @Test
    void profile_update_ignoresSensitiveFields() throws Exception {
        // 尝试夹带 id/userId/status/auditStatus/role，后端白名单必须忽略
        String body = "{\"storeName\":\"合法名称\",\"userId\":" + MERCHANT_B + ",\"id\":99999,"
                + "\"status\":\"SUSPENDED\",\"auditStatus\":\"REJECTED\",\"role\":\"ADMIN\"}";
        MvcResult r = mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");
        assertEquals(MERCHANT_A, data.path("userId").asLong(), "userId 不接受前端篡改");
        assertEquals("NORMAL", data.path("status").asText(), "经营状态不接受前端篡改");
        assertEquals("APPROVED", data.path("auditStatus").asText(), "审核状态不接受前端篡改");
        assertNotEquals(99999, data.path("id").asLong(), "店铺 ID 不接受前端篡改");
    }

    @Test
    void profile_update_blankStoreName_badRequest() throws Exception {
        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON).content(profileBody("   ", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profile_update_invalidPhone_badRequest() throws Exception {
        mockMvc.perform(put("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))
                        .contentType(MediaType.APPLICATION_JSON).content(profileBody("合法名称", "12345")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profile_isolation_merchantCannotSeeOtherProfile() throws Exception {
        JsonNode a = jsonObj(mockMvc.perform(get("/api/v1/merchant/profile")
                .header("Authorization", token(MERCHANT_A, RoleCode.MERCHANT))).andReturn()).path("data");
        JsonNode b = jsonObj(mockMvc.perform(get("/api/v1/merchant/profile")
                .header("Authorization", token(MERCHANT_B, RoleCode.MERCHANT))).andReturn()).path("data");
        assertEquals("甲店铺原始名", a.path("storeName").asText());
        assertEquals("乙店铺原始名", b.path("storeName").asText());
        assertNotEquals(a.path("id").asLong(), b.path("id").asLong());
    }

    @Test
    void profile_lazyInit_createsDefaultWhenMissing() throws Exception {
        // 新商家尚无资料：删除后首次查询应自动创建默认资料，页面不空白
        jdbcTemplate.update("DELETE FROM merchant_profile WHERE user_id = ?", MERCHANT_B);
        MvcResult r = mockMvc.perform(get("/api/v1/merchant/profile")
                        .header("Authorization", token(MERCHANT_B, RoleCode.MERCHANT)))
                .andExpect(status().isOk()).andReturn();
        JsonNode data = jsonObj(r).path("data");
        assertTrue(data.path("storeName").asText().contains("熊猫乙店铺"), "默认店铺名回退到用户昵称");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_profile WHERE user_id = ?", Integer.class, MERCHANT_B);
        assertEquals(1, count);
    }
}
