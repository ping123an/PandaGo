package com.pandago.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.cart.entity.CartItem;
import com.pandago.cart.mapper.CartItemMapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderTransactionService;
import com.pandago.order.support.OrderDraft;
import com.pandago.order.support.OrderFaultHook;
import com.pandago.order.support.OrderNoGenerator;
import com.pandago.order.vo.OrderCreateVO;
import com.pandago.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单事务性写入（阶段 6 核心）。
 *
 * <p><b>事务边界就在这个独立 Bean 的 commit 方法上</b>（{@code @Transactional(rollbackFor = Exception.class)}）。
 * 之所以单独拆成 OrderTransactionService 而不是写在 OrderServiceImpl 里被同类方法直接调用，
 * 是为了避开 Spring AOP 的"自调用陷阱"：同类内部 {@code this.xxx()} 不经过代理，注解会失效。
 * Controller → OrderServiceImpl.createOrder（校验，无事务） → 注入的 OrderTransactionService.commit（事务写入），
 * 调用跨 Bean 经代理，事务必定生效。</p>
 *
 * <p>事务内顺序（对应 PRD 步骤 10–17）：
 * 条件扣库存（受影响行数校验）→ 订单主表（含地址/金额快照）→ 订单明细（商品快照）→ 清理购物车。
 * 任意步骤抛异常（RuntimeException 或 Error），Spring 回滚整个事务，已扣的库存、已插的单据全部撤销。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderTransactionServiceImpl implements OrderTransactionService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderFaultHook faultHook;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO commit(List<OrderDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "没有可结算的商品");
        }

        List<OrderCreateVO.OrderSummary> summaries = new ArrayList<>(drafts.size());
        Orders firstOrder = null;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (OrderDraft draft : drafts) {
            // 11. 条件扣库存：status/audit/deleted/stock 全在 UPDATE 的 WHERE 里，由数据库保证原子性
            for (OrderDraft.DraftLine line : draft.getLines()) {
                int rows = productMapper.deductStock(line.getProductId(), line.getQuantity());
                if (rows != 1) {
                    // 0 行意味着库存不足或商品在校验后被下架/删除；抛异常触发整体回滚
                    throw new BusinessException(ResultCode.CONFLICT,
                            "商品「" + line.getProductName() + "」库存不足或已不可购买，请返回购物车重新确认");
                }
            }
            faultHook.afterStockDeduct();

            // 12 + 14 + 15. 订单主表：订单号/金额/收货地址/商家全部来自校验阶段生成的快照
            faultHook.beforeOrderInsert();
            Orders order = new Orders();
            order.setOrderNo(orderNoGenerator.next());
            order.setUserId(draft.getUserId());
            order.setMerchantId(draft.getMerchantId());
            order.setCheckoutGroupId(draft.getCheckoutGroupId());
            order.setSource(draft.getSource() == null ? "CART" : draft.getSource());
            order.setTotalAmount(draft.getTotalAmount());
            order.setStatus(OrderStatus.PENDING_PAYMENT.getValue());
            order.setRemark(draft.getRemark());
            order.setReceiverName(draft.getReceiverName());
            order.setReceiverPhone(draft.getReceiverPhone());
            order.setReceiverProvince(draft.getReceiverProvince());
            order.setReceiverCity(draft.getReceiverCity());
            order.setReceiverDistrict(draft.getReceiverDistrict());
            order.setReceiverDetailAddress(draft.getReceiverDetailAddress());
            try {
                if (orderMapper.insert(order) != 1) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "订单主表写入失败");
                }
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 幂等：同一结算批次同一商家已建单（重复点击/重复请求）→ 整批回滚，不重复扣库存
                throw new BusinessException(ResultCode.CONFLICT, "订单已提交，请勿重复提交");
            }

            // 13 + 14. 订单明细：商品名/图片/单价/数量/小计全部写快照，不引用商品当前数据
            for (OrderDraft.DraftLine line : draft.getLines()) {
                faultHook.beforeOrderItemInsert();
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setProductId(line.getProductId());
                item.setProductName(line.getProductName());
                item.setProductImage(line.getProductImage());
                item.setUnitPrice(line.getUnitPrice());
                item.setQuantity(line.getQuantity());
                item.setSubtotal(line.getSubtotal());
                if (orderItemMapper.insert(item) != 1) {
                    throw new BusinessException(ResultCode.INTERNAL_ERROR, "订单明细写入失败");
                }
            }

            // 16. 只清理本次成功结算的购物车条目；带上 userId 条件，绝不误删他人数据
            for (OrderDraft.DraftLine line : draft.getLines()) {
                if (line.getCartItemId() != null) {
                    cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                            .eq(CartItem::getId, line.getCartItemId())
                            .eq(CartItem::getUserId, draft.getUserId()));
                }
            }

            if (firstOrder == null) {
                firstOrder = order;
            }
            grandTotal = grandTotal.add(order.getTotalAmount());
            OrderCreateVO.OrderSummary summary = new OrderCreateVO.OrderSummary();
            summary.setId(order.getId());
            summary.setOrderNo(order.getOrderNo());
            summary.setMerchantId(order.getMerchantId());
            summary.setTotalAmount(order.getTotalAmount());
            summary.setStatus(order.getStatus());
            summary.setStatusDescription(OrderStatus.describe(order.getStatus()));
            summaries.add(summary);
        }

        // 17. 事务方法正常返回 → Spring 提交；此时所有写入对用户可见
        OrderCreateVO vo = new OrderCreateVO();
        vo.setId(firstOrder.getId());
        vo.setOrderNo(firstOrder.getOrderNo());
        vo.setTotalAmount(firstOrder.getTotalAmount());
        vo.setStatus(firstOrder.getStatus());
        vo.setStatusDescription(OrderStatus.describe(firstOrder.getStatus()));
        vo.setCheckoutGroupId(drafts.get(0).getCheckoutGroupId());
        vo.setOrderCount(summaries.size());
        vo.setGrandTotal(grandTotal.setScale(2, java.math.RoundingMode.HALF_UP));
        vo.setOrders(summaries);
        return vo;
    }
}
