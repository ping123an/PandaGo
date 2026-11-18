package com.pandago;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.address.entity.UserAddress;
import com.pandago.address.mapper.UserAddressMapper;
import com.pandago.cart.entity.CartItem;
import com.pandago.cart.mapper.CartItemMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7 购物车结算拆单验收测试（7 大场景）。
 *
 * <p>覆盖：单商家 / 双商家 / 三商家 / 库存不足回滚 / 重复提交幂等 /
 * 取消勾选只结算已选 / 多商家混合价格快照与金额校验。</p>
 *
 * <p>不用类级 @Transactional：拆单、库存回滚、幂等都要在真实提交后查库验证。
 * 每个用例用固定测试用户 + 随机商品名，@AfterEach 清理，避免污染演示数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutSplitTests {

    private static final long USER = 93001L;
    private static final String MAIN_IMAGE = "/images/panda-product.svg";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ProductMapper productMapper;
    @Autowired private CartItemMapper cartItemMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", USER);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", USER);
        jdbcTemplate.update("DELETE FROM cart_item WHERE user_id = ?", USER);
        jdbcTemplate.update("DELETE FROM user_address WHERE user_id = ?", USER);
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'CS-%'");
    }

    private String token() {
        return "Bearer " + jwtTokenProvider.generateToken(USER, "user-" + USER, List.of(RoleCode.USER));
    }

    private long categoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private long product(String tag, String price, int stock, long merchantId) throws Exception {
        Product p = new Product();
        p.setCategoryId(categoryId());
        p.setMerchantId(merchantId);
        p.setName("CS-" + tag + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        p.setMainImage(MAIN_IMAGE);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setSales(0);
        p.setStatus(ProductStatus.ON_SALE.getValue());
        p.setAuditStatus(AuditStatus.APPROVED.getValue());
        p.setSort(0);
        productMapper.insert(p);
        return p.getId();
    }

    private long address() {
        UserAddress a = new UserAddress();
        a.setUserId(USER);
        a.setReceiverName("拆单收货人");
        a.setPhone("13800138000");
        a.setProvince("浙江省");
        a.setCity("杭州市");
        a.setDistrict("西湖区");
        a.setDetailAddress("熊猫路 1 号");
        a.setIsDefault(0);
        addressMapper.insert(a);
        return a.getId();
    }

    private String directBody(long addressId, String groupId, Map<Long, Integer> items) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("checkoutGroupId", groupId);
        List<Map<String, Object>> lines = new ArrayList<>();
        items.forEach((pid, qty) -> lines.add(Map.of("productId", pid, "quantity", qty)));
        body.put("items", lines);
        return objectMapper.writeValueAsString(body);
    }

    private String cartBody(long addressId, String groupId, List<Long> cartItemIds) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("checkoutGroupId", groupId);
        body.put("cartItemIds", cartItemIds);
        return objectMapper.writeValueAsString(body);
    }

    private long cartItem(long productId, int quantity, boolean selected) {
        CartItem c = new CartItem();
        c.setUserId(USER);
        c.setProductId(productId);
        c.setQuantity(quantity);
        c.setSelected(selected ? 1 : 0);
        cartItemMapper.insert(c);
        return c.getId();
    }

    private int stockOf(long productId) {
        Integer s = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        return s == null ? -1 : s;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private String gid() {
        return "grp-" + UUID.randomUUID().toString().replace("-", "");
    }

    // 场景1：单商家多商品 → 1 张订单
    @Test
    void scenario1_singleMerchantOneOrder() throws Exception {
        long a = product("M1A", "10.00", 20, 100L);
        long b = product("M1B", "20.00", 20, 100L);
        long addr = address();
        String group = gid();
        Map<Long, Integer> items = new LinkedHashMap<>();
        items.put(a, 2); items.put(b, 3);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addr, group, items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.grandTotal").value(80.00))
                .andExpect(jsonPath("$.data.orders.length()").value(1));

        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ?", group));
        assertEquals(18, stockOf(a));
        assertEquals(17, stockOf(b));
    }

    // 场景2：双商家 → 2 张独立订单，金额各自计算
    @Test
    void scenario2_twoMerchantsTwoOrders() throws Exception {
        long a = product("M1", "30.00", 10, 100L);
        long b = product("M2", "50.00", 10, 200L);
        long addr = address();
        String group = gid();
        Map<Long, Integer> items = new LinkedHashMap<>();
        items.put(a, 2); items.put(b, 1);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addr, group, items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.grandTotal").value(110.00))
                .andExpect(jsonPath("$.data.orders.length()").value(2));

        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ? AND merchant_id = 100 AND total_amount = 60.00", group));
        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ? AND merchant_id = 200 AND total_amount = 50.00", group));
    }

    // 场景3：三商家 → 3 张订单
    @Test
    void scenario3_threeMerchantsThreeOrders() throws Exception {
        long a = product("M1", "11.00", 10, 101L);
        long b = product("M2", "22.00", 10, 102L);
        long c = product("M3", "33.00", 10, 103L);
        long addr = address();
        String group = gid();
        Map<Long, Integer> items = new LinkedHashMap<>();
        items.put(a, 1); items.put(b, 1); items.put(c, 1);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addr, group, items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(3))
                .andExpect(jsonPath("$.data.grandTotal").value(66.00))
                .andExpect(jsonPath("$.data.orders.length()").value(3));

        assertEquals(3, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ?", group));
    }

    // 场景4：库存不足 → 明确商品名 + 整批回滚（库存、订单均不变）
    @Test
    void scenario4_insufficientStockRollsBackAll() throws Exception {
        long a = product("OK", "10.00", 10, 100L);
        long b = product("SHORT", "20.00", 1, 200L);
        long addr = address();
        String group = gid();
        int aBefore = stockOf(a), bBefore = stockOf(b);
        Map<Long, Integer> items = new LinkedHashMap<>();
        items.put(a, 2); items.put(b, 5); // b 库存仅 1

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addr, group, items)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SHORT")));

        assertEquals(aBefore, stockOf(a), "整批回滚：a 库存不得被扣");
        assertEquals(bBefore, stockOf(b));
        assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ?", group), "整批回滚：不得产生任何订单");
    }

    // 场景5：重复提交 → 同批次同商家唯一键拦截，不重复建单/扣库存
    @Test
    void scenario5_duplicateSubmitBlocked() throws Exception {
        long a = product("DUP", "15.00", 20, 100L);
        long addr = address();
        String group = gid();
        String body = directBody(addr, group, Map.of(a, 3));
        int before = stockOf(a);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(1));

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ?", group), "重复提交只能有一张订单");
        assertEquals(before - 3, stockOf(a), "重复提交不得二次扣库存");
    }

    // 场景6：购物车取消勾选 → 前端只提交已选条目，未选条目保留且不扣库存
    @Test
    void scenario6_onlySelectedCartItemsCheckedOut() throws Exception {
        long a = product("SEL", "12.00", 20, 100L);
        long b = product("UNSEL", "12.00", 20, 200L);
        long addr = address();
        long selected = cartItem(a, 2, true);
        long unselected = cartItem(b, 4, false);
        String group = gid();
        int aBefore = stockOf(a), bBefore = stockOf(b);

        // 前端仅把"已勾选且可购买"的条目 ID 提交给后端
        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartBody(addr, group, List.of(selected))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.grandTotal").value(24.00));

        assertFalse(cartExists(selected), "已结算条目应被清理");
        assertTrue(cartExists(unselected), "未勾选条目必须保留在购物车");
        assertEquals(aBefore - 2, stockOf(a), "已选条目扣库存");
        assertEquals(bBefore, stockOf(b), "未选条目不得扣库存");
        assertEquals(0, count("SELECT COUNT(*) FROM order_item WHERE product_id = ?", b), "未选商品不得进入订单");
    }

    // 场景7：多商家混合商品 + 价格快照 → 金额后端计算、前端伪造字段无效
    @Test
    void scenario7_mixedGoodsPriceSnapshot() throws Exception {
        long a = product("P1", "99.00", 10, 100L);
        long b = product("P2", "5.50", 10, 100L);
        long c = product("P3", "200.00", 10, 300L);
        long addr = address();
        String group = gid();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addr);
        body.put("checkoutGroupId", group);
        body.put("totalAmount", 0.01); // 前端伪造金额
        body.put("items", List.of(
                Map.of("productId", a, "quantity", 1, "price", 0.01),
                Map.of("productId", b, "quantity", 2, "price", 0.01),
                Map.of("productId", c, "quantity", 1, "price", 0.01)));

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.grandTotal").value(310.00)); // 99+5.5*2+200

        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ? AND merchant_id = 100 AND total_amount = 110.00", group));
        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ? AND merchant_id = 300 AND total_amount = 200.00", group));
        assertEquals(1, count("SELECT COUNT(*) FROM order_item WHERE product_id = ? AND unit_price = 99.00 AND subtotal = 99.00", a), "价格快照应取数据库真实价");
        assertEquals(1, count("SELECT COUNT(*) FROM order_item WHERE product_id = ? AND unit_price = 5.50 AND subtotal = 11.00", b));
        assertTrue(count("SELECT COUNT(*) FROM orders WHERE checkout_group_id = ? AND total_amount = 0.01", group) == 0, "伪造金额必须被忽略");
    }

    // 附加：购物车返回真实商家 ID 与名称，供前端按商家分组
    @Test
    void cartItemsCarryMerchantInfo() throws Exception {
        long a = product("M1", "10.00", 10, 48L); // 商家 merchant_demo(id=48)
        cartItem(a, 1, true);

        mockMvc.perform(get("/api/v1/cart").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].merchantId").value(48))
                .andExpect(jsonPath("$.data.items[0].merchantName").exists());
    }

    private boolean cartExists(long id) {
        return count("SELECT COUNT(*) FROM cart_item WHERE id = ?", id) == 1;
    }
}
