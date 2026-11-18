package com.pandago;

import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderTimeoutCloseService;
import com.pandago.order.service.OrderTimeoutScanService;
import com.pandago.order.support.OrderTimeoutFaultHook;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

/**
 * 超时订单定时关闭集成测试（阶段 9）。
 *
 * <p>不使用类级事务：条件状态更新、库存回补与回滚必须在真实提交/回滚后查库验证。
 * 每例用独立订单号与商品，{@code @AfterEach} 清理所有测试数据。
 * 后台调度在测试环境关闭，统一手动调用 {@link OrderTimeoutScanService#scanAndClose()}。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class Stage9TimeoutOrderTests {

    private static final long TEST_USER = 94501L;
    private static final long TEST_MERCHANT = 94502L;

    @Autowired private OrderMapper orderMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderTimeoutScanService scanService;
    @Autowired private OrderTimeoutCloseService closeService;

    /** 故障注入点：默认空操作，事务一致性用例里让回补前抛异常。 */
    @MockBean private OrderTimeoutFaultHook faultHook;

    private Long categoryId() {
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE status = 1 ORDER BY id LIMIT 1", Long.class);
    }

    /** 建一个已扣库存的待支付订单，可指定创建时间；返回订单 ID 与商品 ID。 */
    private long[] createPendingOrder(LocalDateTime createdAt, int qty) {
        Product product = new Product();
        product.setCategoryId(categoryId());
        product.setMerchantId(TEST_MERCHANT);
        product.setName("S9-TIMEOUT-" + UUID.randomUUID().toString().substring(0, 8));
        product.setMainImage("/images/panda-product.svg");
        product.setPrice(new BigDecimal("50.00"));
        product.setStock(100);
        product.setSales(0);
        product.setStatus(ProductStatus.ON_SALE.getValue());
        product.setAuditStatus(AuditStatus.APPROVED.getValue());
        product.setSort(0);
        productMapper.insert(product);

        // 模拟阶段 6 下单已扣库存：stock 100 -> 100-qty
        productMapper.deductStock(product.getId(), qty);

        Orders order = new Orders();
        order.setOrderNo("S9T" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        order.setUserId(TEST_USER);
        order.setMerchantId(TEST_MERCHANT);
        order.setTotalAmount(new BigDecimal("50.00").multiply(BigDecimal.valueOf(qty)));
        order.setStatus(OrderStatus.PENDING_PAYMENT.getValue());
        order.setReceiverName("测试用户");
        order.setReceiverPhone("13800000000");
        order.setReceiverProvince("广东省");
        order.setReceiverCity("深圳市");
        order.setReceiverDistrict("南山区");
        order.setReceiverDetailAddress("测试地址 1 号");
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt);
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductImage(product.getMainImage());
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setQuantity(qty);
        item.setSubtotal(new BigDecimal("50.00").multiply(BigDecimal.valueOf(qty)));
        item.setCreatedAt(createdAt);
        orderItemMapper.insert(item);

        return new long[]{order.getId(), product.getId()};
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", TEST_USER);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", TEST_USER);
        jdbcTemplate.update("DELETE FROM product WHERE name LIKE 'S9-TIMEOUT-%'");
    }

    @Test
    void timedOutOrderIsClosedAndStockRestored() {
        long[] fixture = createPendingOrder(LocalDateTime.now().minusHours(2), 3);
        long orderId = fixture[0];
        long productId = fixture[1];
        assertEquals(97, productMapper.selectById(productId).getStock(), "下单应已扣 3 件");

        int closed = scanService.scanAndClose();

        assertTrue(closed >= 1, "应至少关闭本次构造的超时订单");
        assertEquals(OrderStatus.CANCELLED.getValue(), orderMapper.selectById(orderId).getStatus());
        assertEquals(100, productMapper.selectById(productId).getStock(), "库存应只回补一次");
    }

    @Test
    void notTimedOutOrderStaysPending() {
        long[] fixture = createPendingOrder(LocalDateTime.now().minusMinutes(5), 2);
        long orderId = fixture[0];
        long productId = fixture[1];

        scanService.scanAndClose();

        assertEquals(OrderStatus.PENDING_PAYMENT.getValue(), orderMapper.selectById(orderId).getStatus(),
                "5 分钟前创建、未到 30 分钟超时阈值的订单不应被关闭");
        assertEquals(98, productMapper.selectById(productId).getStock(), "未关闭不应回补库存");
    }

    @Test
    void paidOrderIsNotClosed() {
        long[] fixture = createPendingOrder(LocalDateTime.now().minusHours(3), 1);
        long orderId = fixture[0];
        long productId = fixture[1];
        // 模拟已支付：状态不再是 PENDING_PAYMENT
        jdbcTemplate.update("UPDATE orders SET status = ? WHERE id = ?",
                OrderStatus.PENDING_SHIPMENT.getValue(), orderId);

        scanService.scanAndClose();

        assertEquals(OrderStatus.PENDING_SHIPMENT.getValue(), orderMapper.selectById(orderId).getStatus(),
                "已支付订单即使创建时间很久，也不能被普通超时任务关闭");
        assertEquals(99, productMapper.selectById(productId).getStock(), "已支付订单库存不回补");
    }

    @Test
    void repeatedCloseRestoresStockOnlyOnce() {
        long[] fixture = createPendingOrder(LocalDateTime.now().minusHours(2), 4);
        long orderId = fixture[0];
        long productId = fixture[1];
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);

        assertTrue(closeService.closeOne(orderId, cutoff), "首次关闭应成功");
        assertEquals(100, productMapper.selectById(productId).getStock(), "首次关闭应回补 4 件回到 100");

        // 再次执行：条件 UPDATE 影响 0 行，不回补库存
        assertFalse(closeService.closeOne(orderId, cutoff), "重复关闭应跳过");
        assertFalse(closeService.closeOne(orderId, cutoff), "多次重复仍跳过");
        assertEquals(100, productMapper.selectById(productId).getStock(), "重复执行不能重复恢复库存");
    }

    @Test
    void restockFailureRollsBackStatusAndStockTogether() {
        long[] fixture = createPendingOrder(LocalDateTime.now().minusHours(2), 5);
        long orderId = fixture[0];
        long productId = fixture[1];
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        doThrow(new RuntimeException("模拟库存回补失败"))
                .when(faultHook).beforeRestock(anyLong());

        boolean thrown = false;
        try {
            closeService.closeOne(orderId, cutoff);
        } catch (RuntimeException e) {
            thrown = true;
        }

        assertTrue(thrown, "回补失败应抛异常以触发回滚");
        assertEquals(OrderStatus.PENDING_PAYMENT.getValue(), orderMapper.selectById(orderId).getStatus(),
                "事务回滚后订单状态必须回到待支付");
        assertEquals(95, productMapper.selectById(productId).getStock(),
                "事务回滚后库存必须保持下单扣减后的值，不与订单状态脱节");
    }
}
