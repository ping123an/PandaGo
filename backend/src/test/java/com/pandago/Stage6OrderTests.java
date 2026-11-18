package com.pandago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.address.entity.UserAddress;
import com.pandago.address.mapper.UserAddressMapper;
import com.pandago.cart.entity.CartItem;
import com.pandago.cart.mapper.CartItemMapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.order.support.OrderFaultHook;
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

import java.math.BigDecimal;
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
 * 阶段 6 订单创建、事务回滚与查询集成测试。
 *
 * <p>不使用类级 @Transactional：事务回滚用例必须在方法真实提交/回滚后查询数据库验证，
 * 外层测试事务会干扰判断。每个用例用独立随机数据，@AfterEach 统一清理测试数据。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Stage6OrderTests {

    private static final long USER_A = 92001L;
    private static final long USER_B = 92002L;
    private static final String MAIN_IMAGE = "/images/panda-product.svg";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ProductMapper productMapper;
    @Autowired private CartItemMapper cartItemMapper;
    @Autowired private UserAddressMapper addressMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 故障注入点：默认空实现被替换为 mock，reset 后所有方法为空操作 */
    @MockBean private OrderFaultHook faultHook;

    @BeforeEach
    void resetHook() {
        Mockito.reset(faultHook);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (?,?))", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM cart_item WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM user_address WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'S6-%'");
    }

    private String token(long userId) {
        return "Bearer " + jwtTokenProvider.generateToken(userId, "user-" + userId, List.of(RoleCode.USER));
    }

    private long firstCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private long insertProduct(String name, BigDecimal price, int stock, String status, String audit, long merchantId) throws Exception {
        Product product = new Product();
        product.setCategoryId(firstCategoryId());
        product.setMerchantId(merchantId);
        product.setName(name);
        product.setMainImage(MAIN_IMAGE);
        product.setPrice(price);
        product.setStock(stock);
        product.setSales(0);
        product.setStatus(status);
        product.setAuditStatus(audit);
        product.setSort(0);
        productMapper.insert(product);
        return product.getId();
    }

    private long insertSellableProduct(BigDecimal price, int stock) throws Exception {
        return insertProduct("S6-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                price, stock, ProductStatus.ON_SALE.getValue(), AuditStatus.APPROVED.getValue(), 0L);
    }

    private long insertAddress(long userId) {
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName("测试收货人");
        address.setPhone("13800138000");
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDistrict("西湖区");
        address.setDetailAddress("熊猫路 1 号");
        address.setIsDefault(0);
        addressMapper.insert(address);
        return address.getId();
    }

    private int stockOf(long productId) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, productId);
        return stock == null ? -1 : stock;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private String directBody(long addressId, long productId, int quantity) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("items", List.of(Map.of("productId", productId, "quantity", quantity)));
        body.put("remark", "阶段6测试");
        return objectMapper.writeValueAsString(body);
    }

    // 1. 正常创建订单
    @Test
    void createOrderSuccessfully() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("199.00"), 10);
        long addressId = insertAddress(USER_A);
        int before = stockOf(productId);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").exists())
                .andExpect(jsonPath("$.data.totalAmount").value(398.00))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.statusDescription").value("待支付"));

        assertEquals(before - 2, stockOf(productId), "库存应被条件扣减 2");
        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE user_id = ? AND total_amount = 398.00", USER_A));
        assertEquals(1, count("SELECT COUNT(*) FROM order_item WHERE product_id = ? AND unit_price = 199.00 AND subtotal = 398.00", productId));
    }

    // 2. 商品不存在
    @Test
    void productNotFound() throws Exception {
        long addressId = insertAddress(USER_A);
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, 999999999L, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // 3. 商品下架
    @Test
    void offShelfProductRejected() throws Exception {
        long productId = insertProduct("S6-OFF-" + UUID.randomUUID(), new BigDecimal("99.00"), 5,
                ProductStatus.OFF_SALE.getValue(), AuditStatus.APPROVED.getValue(), 0L);
        long addressId = insertAddress(USER_A);
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // 4. 商品未审核
    @Test
    void unauditedProductRejected() throws Exception {
        long productId = insertProduct("S6-PEND-" + UUID.randomUUID(), new BigDecimal("99.00"), 5,
                ProductStatus.ON_SALE.getValue(), AuditStatus.PENDING.getValue(), 0L);
        long addressId = insertAddress(USER_A);
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // 5. 库存不足
    @Test
    void insufficientStockRejected() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("50.00"), 1);
        long addressId = insertAddress(USER_A);
        int before = stockOf(productId);
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
        assertEquals(before, stockOf(productId), "库存不足不得扣减");
    }

    // 6. 地址不属于当前用户
    @Test
    void addressOfAnotherUserRejected() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("99.00"), 5);
        long addressId = insertAddress(USER_B);
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // 7. 商品跨商家下单：自动按商家拆成多张订单（V7 拆单后行为变更）
    @Test
    void crossMerchantSplitIntoOrders() throws Exception {
        long p1 = insertProduct("S6-M1-" + UUID.randomUUID(), new BigDecimal("99.00"), 5,
                ProductStatus.ON_SALE.getValue(), AuditStatus.APPROVED.getValue(), 1L);
        long p2 = insertProduct("S6-M2-" + UUID.randomUUID(), new BigDecimal("88.00"), 5,
                ProductStatus.ON_SALE.getValue(), AuditStatus.APPROVED.getValue(), 2L);
        long addressId = insertAddress(USER_A);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("checkoutGroupId", "grp-" + UUID.randomUUID().toString().replace("-", ""));
        body.put("items", List.of(
                Map.of("productId", p1, "quantity", 1),
                Map.of("productId", p2, "quantity", 1)));
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.grandTotal").value(187.00))
                .andExpect(jsonPath("$.data.orders.length()").value(2));

        assertEquals(2, count("SELECT COUNT(*) FROM orders WHERE user_id = ? AND checkout_group_id IS NOT NULL", USER_A),
                "两个商家应生成两张独立订单");
    }

    // 8. 前端篡改价格/用户/商家字段被忽略
    @Test
    void tamperedPriceAndIdentityFieldsIgnored() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("599.00"), 10);
        long addressId = insertAddress(USER_A);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("userId", USER_B);
        body.put("merchantId", 888L);
        body.put("totalAmount", 0.01);
        body.put("price", 0.01);
        body.put("payAmount", 0.01);
        body.put("items", List.of(Map.of("productId", productId, "quantity", 1,
                "unitPrice", 0.01, "price", 0.01)));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(599.00));

        assertEquals(1, count("SELECT COUNT(*) FROM orders WHERE user_id = ? AND total_amount = 599.00", USER_A));
        assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE user_id = ?", USER_B),
                "前端伪造的 userId 必须被忽略");
    }

    // 9. 订单主表写入失败时事务回滚
    @Test
    void orderInsertFailureRollsBack() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("123.00"), 10);
        long addressId = insertAddress(USER_A);
        int before = stockOf(productId);
        Mockito.doThrow(new RuntimeException("模拟订单主表写入失败")).when(faultHook).beforeOrderInsert();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 3)))
                .andExpect(status().isInternalServerError());

        assertEquals(before, stockOf(productId), "主表写入失败应回滚已扣库存");
        assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE user_id = ?", USER_A), "不应残留订单");
        assertEquals(0, count("SELECT COUNT(*) FROM order_item WHERE product_id = ?", productId));
    }

    // 10. 订单明细写入失败时事务回滚
    @Test
    void orderItemInsertFailureRollsBack() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("77.00"), 10);
        long addressId = insertAddress(USER_A);
        int before = stockOf(productId);
        Mockito.doThrow(new RuntimeException("模拟明细写入失败")).when(faultHook).beforeOrderItemInsert();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 2)))
                .andExpect(status().isInternalServerError());

        assertEquals(before, stockOf(productId), "明细失败应回滚库存");
        assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE user_id = ?", USER_A), "主表也应回滚");
        assertEquals(0, count("SELECT COUNT(*) FROM order_item WHERE product_id = ?", productId));
    }

    // 11. 库存扣减后其他步骤失败时库存回滚
    @Test
    void stockDeductedThenFailureRollsBack() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("66.00"), 7);
        long addressId = insertAddress(USER_A);
        int before = stockOf(productId);
        Mockito.doThrow(new RuntimeException("模拟扣库存后失败")).when(faultHook).afterStockDeduct();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 4)))
                .andExpect(status().isInternalServerError());

        assertEquals(before, stockOf(productId), "扣库存后失败必须恢复库存");
        assertEquals(0, count("SELECT COUNT(*) FROM orders WHERE user_id = ?", USER_A));
    }

    // 12. 查询其他用户订单
    @Test
    void cannotViewOtherUsersOrder() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("88.00"), 10);
        long addressId = insertAddress(USER_A);
        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body).path("data");
        long orderId = created.path("id").asLong();

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", token(USER_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiverName").value("测试收货人"))
                .andExpect(jsonPath("$.data.items[0].productName").exists());
    }

    // 补充：购物车模式创建订单后只清理已结算条目
    @Test
    void cartCheckoutClearsOnlySubmittedItems() throws Exception {
        long p1 = insertSellableProduct(new BigDecimal("30.00"), 20);
        long p2 = insertSellableProduct(new BigDecimal("40.00"), 20);
        long addressId = insertAddress(USER_A);
        CartItem c1 = new CartItem();
        c1.setUserId(USER_A); c1.setProductId(p1); c1.setQuantity(2); c1.setSelected(1);
        cartItemMapper.insert(c1);
        CartItem c2 = new CartItem();
        c2.setUserId(USER_A); c2.setProductId(p2); c2.setQuantity(1); c2.setSelected(1);
        cartItemMapper.insert(c2);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressId", addressId);
        body.put("cartItemIds", List.of(c1.getId()));
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(60.00));

        assertFalse(cartItemExists(c1.getId()), "已结算条目应被清理");
        assertTrue(cartItemExists(c2.getId()), "未提交条目应保留");
    }

    private boolean cartItemExists(long id) {
        return count("SELECT COUNT(*) FROM cart_item WHERE id = ?", id) == 1;
    }

    // 补充：订单快照不受商品后续改名影响
    @Test
    void orderSnapshotUnaffectedByProductRename() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("15.00"), 30);
        long addressId = insertAddress(USER_A);
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", token(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(directBody(addressId, productId, 1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(response).path("data").path("id").asLong();
        String originalName = jdbcTemplate.queryForObject("SELECT product_name FROM order_item WHERE order_id = ?", String.class, orderId);
        jdbcTemplate.update("UPDATE product SET name = ?, price = 999.00 WHERE id = ?", "S6-改名后", productId);

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productName").value(originalName))
                .andExpect(jsonPath("$.data.totalAmount").value(15.00));
    }

    // 补充：分页与状态筛选
    @Test
    void listAndFilterOrders() throws Exception {
        long productId = insertSellableProduct(new BigDecimal("12.00"), 30);
        long addressId = insertAddress(USER_A);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/orders")
                    .header("Authorization", token(USER_A))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(directBody(addressId, productId, 1)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/orders")
                        .param("pageNum", "1").param("pageSize", "10")
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].status").value("PENDING_PAYMENT"));
        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "COMPLETED")
                        .header("Authorization", token(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // 补充：未登录访问订单接口被拒绝
    @Test
    void unauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
