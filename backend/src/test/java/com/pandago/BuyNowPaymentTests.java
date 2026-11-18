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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 立即购买 + 支付流程验收（11 场景）。
 *
 * <p>覆盖：预览、直接购买、数量、购物车隔离、库存不足、下单、模拟支付、
 * 取消支付（不删单）、重复支付、购物车拆单兼容、越权/价格篡改安全。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuyNowPaymentTests {

    private static final long USER = 94001L;
    private static final long OTHER = 94002L;
    private static final String IMG = "/images/panda-product.svg";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ProductMapper productMapper;
    @Autowired private CartItemMapper cartItemMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))", USER, OTHER);
        jdbcTemplate.update("DELETE FROM payment_record WHERE user_id IN (?,?)", USER, OTHER);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id IN (?,?)", USER, OTHER);
        jdbcTemplate.update("DELETE FROM cart_item WHERE user_id IN (?,?)", USER, OTHER);
        jdbcTemplate.update("DELETE FROM user_address WHERE user_id IN (?,?)", USER, OTHER);
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'BN-%'");
    }

    private String token(long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId, List.of(RoleCode.USER));
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
        p.setName("BN-" + tag + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        p.setMainImage(IMG);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setSales(0);
        p.setStatus(ProductStatus.ON_SALE.getValue());
        p.setAuditStatus(AuditStatus.APPROVED.getValue());
        p.setSort(0);
        productMapper.insert(p);
        return p.getId();
    }

    private long address(long userId) {
        UserAddress a = new UserAddress();
        a.setUserId(userId);
        a.setReceiverName("立即购买收货人");
        a.setPhone("13800138000");
        a.setProvince("浙江省");
        a.setCity("杭州市");
        a.setDistrict("西湖区");
        a.setDetailAddress("熊猫路 1 号");
        a.setIsDefault(1);
        addressMapper.insert(a);
        return a.getId();
    }

    private String body(Long addressId, String source, List<Map<String, Object>> items) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("addressId", addressId);
        if (source != null) m.put("source", source);
        m.put("items", items);
        return objectMapper.writeValueAsString(m);
    }

    private Map<String, Object> line(long productId, int qty) {
        return Map.of("productId", productId, "quantity", qty);
    }

    private int stockOf(long id) {
        Integer s = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id);
        return s == null ? -1 : s;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    // 场景1：立即购买预览 + 下单，商品不进购物车
    @Test
    void scenario1_buyNowPreviewAndCreate() throws Exception {
        long p = product("WATCH", "599.00", 20, 300L);
        long addr = address(USER);

        mockMvc.perform(post("/api/v1/orders/preview").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("DIRECT"))
                .andExpect(jsonPath("$.data.payableAmount").value(599.00))
                .andExpect(jsonPath("$.data.merchantCount").value(1))
                .andExpect(jsonPath("$.data.items[0].specText").exists());

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(1))
                .andExpect(jsonPath("$.data.grandTotal").value(599.00));

        assertEquals(0, count("SELECT COUNT(*) FROM cart_item WHERE user_id = ?", USER), "立即购买不得写入购物车");
        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE user_id = ? AND source = 'DIRECT'", USER), "订单来源应为 DIRECT");
    }

    // 场景2：直接购买多个数量，金额与库存正确
    @Test
    void scenario2_buyNowQuantity3() throws Exception {
        long p = product("QTY3", "12.00", 10, 100L);
        long addr = address(USER);
        int before = stockOf(p);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(36.00));

        assertEquals(before - 3, stockOf(p));
        assertEquals(1, count("SELECT COUNT(*) FROM order_item WHERE product_id = ? AND quantity = 3 AND unit_price = 12.00 AND subtotal = 36.00", p));
    }

    // 场景3：购物车已有其他商家商品，立即购买互不干扰
    @Test
    void scenario3_cartIsolation() throws Exception {
        long cartA = product("CART_A", "2999.00", 5, 1L);
        long cartB = product("CART_B", "399.00", 5, 2L);
        long buyNow = product("CART_WATCH", "599.00", 20, 3L);
        long addr = address(USER);
        CartItem c1 = new CartItem(); c1.setUserId(USER); c1.setProductId(cartA); c1.setQuantity(1); c1.setSelected(1);
        CartItem c2 = new CartItem(); c2.setUserId(USER); c2.setProductId(cartB); c2.setQuantity(1); c2.setSelected(1);
        cartItemMapper.insert(c1); cartItemMapper.insert(c2);
        int aBefore = stockOf(cartA), bBefore = stockOf(cartB);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(buyNow, 1)))))
                .andExpect(status().isOk());

        assertEquals(2, count("SELECT COUNT(*) FROM cart_item WHERE user_id = ?", USER), "购物车原有商品必须保留");
        assertEquals(aBefore, stockOf(cartA), "购物车商品 A 库存不得变化");
        assertEquals(bBefore, stockOf(cartB), "购物车商品 B 库存不得变化");
        assertEquals(0, count("SELECT COUNT(*) FROM order_item WHERE product_id IN (?,?)", cartA, cartB), "购物车商品不得进入本次订单");
    }

    // 场景4：未登录访问预览/下单被 401 拒绝（前端据此引导登录）
    @Test
    void scenario4_unauthorizedBlocked() throws Exception {
        long p = product("AUTH", "50.00", 5, 100L);
        long addr = address(USER);
        mockMvc.perform(post("/api/v1/orders/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, 1)))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, 1)))))
                .andExpect(status().isUnauthorized());
    }

    // 场景5：库存不足阻止下单并给出明确商品名
    @Test
    void scenario5_insufficientStock() throws Exception {
        long p = product("SHORTAGE", "20.00", 1, 100L);
        long addr = address(USER);
        mockMvc.perform(post("/api/v1/orders/preview").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, 5)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SHORTAGE")));
        mockMvc.perform(post("/api/v1/orders").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, 5)))))
                .andExpect(status().isConflict());
        assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE user_id = ?", USER));
    }

    /** 下单并返回订单号 */
    private String createDirect(long addr, long p, int qty, String methodPart) throws Exception {
        String no = mockMvc.perform(post("/api/v1/orders").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(addr, "DIRECT", List.of(line(p, qty)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(no).path("data").path("orderNo").asText();
    }

    // 场景6：提交订单生成订单并进入待支付
    @Test
    void scenario6_submitCreatesPendingOrder() throws Exception {
        long p = product("SUBMIT", "88.00", 10, 100L);
        long addr = address(USER);
        String orderNo = createDirect(addr, p, 1, "");
        mockMvc.perform(get("/api/v1/payments/orders/" + orderNo).header("Authorization", token(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.paid").value(false));
    }

    // 场景7：模拟支付成功，状态流转正确，支付方式被记录
    @Test
    void scenario7_mockPaySuccess() throws Exception {
        long p = product("PAY", "66.00", 10, 100L);
        long addr = address(USER);
        String orderNo = createDirect(addr, p, 1, "");

        mockMvc.perform(post("/api/v1/payments/mock").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\",\"paymentMethod\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid").value(true))
                .andExpect(jsonPath("$.data.paymentMethod").value("ALIPAY"))
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_SHIPMENT"));

        assertEquals(1, count("SELECT COUNT(*) FROM payment_record WHERE order_no = ? AND status='SUCCESS'", orderNo));
    }

    // 场景8：取消支付不删除订单、不标记已支付，可从订单列表继续支付
    @Test
    void scenario8_cancelPaymentKeepsOrder() throws Exception {
        long p = product("CANCEL_PAY", "55.00", 10, 100L);
        long addr = address(USER);
        String orderNo = createDirect(addr, p, 1, "");

        // 取消支付只是不调用支付接口；这里直接查询支付信息验证订单仍在且未支付
        mockMvc.perform(get("/api/v1/payments/orders/" + orderNo).header("Authorization", token(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid").value(false))
                .andExpect(jsonPath("$.data.orderStatus").value("PENDING_PAYMENT"));

        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE order_no = ?", orderNo), "订单必须保留");
        // 之后仍可继续支付
        mockMvc.perform(post("/api/v1/payments/mock").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid").value(true));
    }

    // 场景9：重复支付不重复入账
    @Test
    void scenario9_duplicatePaymentBlocked() throws Exception {
        long p = product("DUPPAY", "77.00", 10, 100L);
        long addr = address(USER);
        String orderNo = createDirect(addr, p, 1, "");
        String payBody = "{\"orderNo\":\"" + orderNo + "\",\"paymentMethod\":\"WECHAT\"}";

        mockMvc.perform(post("/api/v1/payments/mock").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON).content(payBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/mock").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON).content(payBody))
                .andExpect(status().isConflict());

        assertEquals(1, count("SELECT COUNT(*) FROM payment_record WHERE order_no = ?", orderNo), "只应有一条支付记录");
    }

    // 场景10：购物车购买仍正常，多商家拆单兼容
    @Test
    void scenario10_cartCheckoutStillSplits() throws Exception {
        long a = product("CART_M1", "30.00", 10, 100L);
        long b = product("CART_M2", "50.00", 10, 200L);
        long addr = address(USER);
        CartItem c1 = new CartItem(); c1.setUserId(USER); c1.setProductId(a); c1.setQuantity(2); c1.setSelected(1);
        CartItem c2 = new CartItem(); c2.setUserId(USER); c2.setProductId(b); c2.setQuantity(1); c2.setSelected(1);
        cartItemMapper.insert(c1); cartItemMapper.insert(c2);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("addressId", addr);
        m.put("source", "CART");
        m.put("checkoutGroupId", "bn-grp-" + UUID.randomUUID().toString().replace("-", ""));
        m.put("cartItemIds", List.of(c1.getId(), c2.getId()));

        mockMvc.perform(post("/api/v1/orders").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.grandTotal").value(110.00));

        assertEquals(2, count("SELECT COUNT(*) FROM orders WHERE user_id = ? AND source = 'CART'", USER));
        assertEquals(0, count("SELECT COUNT(*) FROM cart_item WHERE user_id = ?", USER), "已结算购物车条目应清理");
    }

    // 场景11：安全 —— 不能支付他人订单；不能篡改价格/来源影响金额
    @Test
    void scenario11_security() throws Exception {
        long p = product("SEC", "999.00", 10, 100L);
        long addr = address(USER);
        String orderNo = createDirect(addr, p, 1, "");

        // 他人不能查询/支付该订单
        mockMvc.perform(get("/api/v1/payments/orders/" + orderNo).header("Authorization", token(OTHER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/payments/mock").header("Authorization", token(OTHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isNotFound());

        // 前端夹带 price/amount/merchantId 不生效
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("addressId", addr);
        m.put("source", "DIRECT");
        m.put("price", 0.01);
        m.put("totalAmount", 0.01);
        m.put("merchantId", 888L);
        m.put("items", List.of(Map.of("productId", p, "quantity", 1, "price", 0.01)));
        String res = mockMvc.perform(post("/api/v1/orders").header("Authorization", token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(m)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(objectMapper.readTree(res).path("data").path("totalAmount").asDouble() == 999.00,
                "金额必须取数据库真实价格");
    }
}
