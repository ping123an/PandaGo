package com.pandago.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pandago.address.entity.UserAddress;
import com.pandago.address.mapper.UserAddressMapper;
import com.pandago.cart.entity.CartItem;
import com.pandago.cart.mapper.CartItemMapper;
import com.pandago.common.api.PageResult;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.dto.OrderCreateDTO;
import com.pandago.order.dto.OrderPreviewDTO;
import com.pandago.order.dto.OrderQueryDTO;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderService;
import com.pandago.order.service.OrderTransactionService;
import com.pandago.order.support.OrderDraft;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.order.vo.OrderCreateVO;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderItemVO;
import com.pandago.order.vo.OrderPreviewVO;
import com.pandago.order.vo.OrderStatusVO;
import com.pandago.order.vo.OrderVO;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单创建（校验阶段）与查询。
 *
 * <p><b>阶段 6 校验流程（PRD 17 步中的 1–9）</b>：本方法只做只读校验并构建 {@link OrderDraft}，
 * 不做任何写入；所有写操作交给注入的 {@link OrderTransactionService#commit} 在单一事务内完成。
 * 这种拆分既满足事务边界清晰，也保证 @Transactional 通过 Spring 代理生效。</p>
 *
 * <p><b>金额与身份安全</b>：userId 取自 SecurityContext；单价只从 product 表读取；
 * DTO 不含任何价格字段，前端即便额外传 price/totalAmount/userId/merchantId 也会被忽略。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderTransactionService orderTransactionService;
    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final UserAddressMapper addressMapper;
    private final UserMapper userMapper;

    @Override
    public OrderCreateVO create(OrderCreateDTO dto) {
        if (dto == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请求参数不能为空");
        }
        // 1. 当前用户：只从 JWT 验签后的 SecurityContext 获取
        Long userId = SecurityUtils.requireCurrentUser().getUserId();

        // 2. 地址归属：必须属于当前用户且未删除
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, dto.getAddressId())
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收货地址不存在或不属于当前用户");
        }

        // 3 + 4. 解析并提交的商品行：购物车模式或直购模式，二选一
        List<OrderDraft.DraftLine> lines = resolveLines(userId, dto);

        // 5–9. 逐行校验商品状态、商家一致性，并读取数据库最新价格计算金额
        List<Product> products = productMapper.selectBatchIds(
                lines.stream().map(OrderDraft.DraftLine::getProductId).toList());
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        // 结算批次号：优先用前端提交的（幂等键），缺省由后端生成
        String checkoutGroupId = (dto.getCheckoutGroupId() == null || dto.getCheckoutGroupId().isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : dto.getCheckoutGroupId();

        // 按商家分组：每个商家一张订单，价格/商家/库存全部来自数据库快照
        Map<Long, List<OrderDraft.DraftLine>> groupByMerchant = new LinkedHashMap<>();
        Map<Long, BigDecimal> totalByMerchant = new HashMap<>();
        for (OrderDraft.DraftLine line : lines) {
            Product product = productMap.get(line.getProductId());
            // 4. 商品存在（逻辑删除由 MP 自动过滤）
            if (product == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已删除");
            }
            // 5. 上架且审核通过
            if (!ProductStatus.ON_SALE.getValue().equals(product.getStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "商品「" + product.getName() + "」已下架，无法下单");
            }
            if (!AuditStatus.APPROVED.getValue().equals(product.getAuditStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "商品「" + product.getName() + "」未审核通过，无法下单");
            }
            // 6. 真实商家来自数据库；null 归为 0（平台演示商品）
            Long currentMerchantId = product.getMerchantId() == null ? 0L : product.getMerchantId();
            // 8. 数量必须为正（DTO 已校验，购物车数据二次兜底）
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            if (quantity <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "购买数量必须大于 0");
            }
            // 7 + 9. 价格来自数据库，不信任前端；小计与总额后端计算
            BigDecimal price = product.getPrice();
            if (price == null) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "商品价格异常");
            }
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
            line.setProductName(product.getName());
            line.setProductImage(product.getMainImage());
            line.setUnitPrice(price.setScale(2, RoundingMode.HALF_UP));
            line.setQuantity(quantity);
            line.setSubtotal(subtotal);
            groupByMerchant.computeIfAbsent(currentMerchantId, k -> new ArrayList<>()).add(line);
            totalByMerchant.merge(currentMerchantId, subtotal, BigDecimal::add);
        }

        // 订单来源：缺省按 CART，兼容旧客户端；DIRECT 仅作来源标记，不改变校验与库存策略
        String source = (dto.getSource() == null || dto.getSource().isBlank())
                ? "CART" : dto.getSource();

        // 每个商家生成一个 OrderDraft，共享地址快照、备注与结算批次号
        List<OrderDraft> drafts = new ArrayList<>(groupByMerchant.size());
        for (Map.Entry<Long, List<OrderDraft.DraftLine>> entry : groupByMerchant.entrySet()) {
            OrderDraft draft = new OrderDraft();
            draft.setUserId(userId);
            draft.setMerchantId(entry.getKey());
            draft.setCheckoutGroupId(checkoutGroupId);
            draft.setSource(source);
            draft.setRemark(dto.getRemark());
            draft.setReceiverName(address.getReceiverName());
            draft.setReceiverPhone(address.getPhone());
            draft.setReceiverProvince(address.getProvince());
            draft.setReceiverCity(address.getCity());
            draft.setReceiverDistrict(address.getDistrict());
            draft.setReceiverDetailAddress(address.getDetailAddress());
            draft.setTotalAmount(totalByMerchant.get(entry.getKey()).setScale(2, RoundingMode.HALF_UP));
            draft.setLines(entry.getValue());
            drafts.add(draft);
        }

        // 10–17. 跨 Bean 调用进入事务写服务；@Transactional 经代理生效
        return orderTransactionService.commit(drafts);
    }

    @Override
    public OrderPreviewVO preview(OrderPreviewDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "没有需要预览的商品");
        }
        Long userId = SecurityUtils.requireCurrentUser().getUserId();

        // 地址必须是当前用户的；预览阶段即校验，避免结算页展示无效地址
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, dto.getAddressId())
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收货地址不存在或不属于当前用户");
        }

        // 同一商品不可重复预览
        Set<Long> productIds = dto.getItems().stream()
                .map(OrderPreviewDTO.OrderItemInput::getProductId).collect(Collectors.toSet());
        if (productIds.size() != dto.getItems().size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同一商品请勿重复提交");
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(new ArrayList<>(productIds))) {
            productMap.put(product.getId(), product);
        }

        String source = (dto.getSource() == null || dto.getSource().isBlank())
                ? "CART" : dto.getSource();

        OrderPreviewVO vo = new OrderPreviewVO();
        vo.setSource(source);
        vo.setDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        vo.setFreightAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        Map<Long, String> merchantNames = new HashMap<>();
        Map<Long, OrderPreviewVO.PreviewMerchantGroup> groupMap = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderPreviewDTO.OrderItemInput input : dto.getItems()) {
            Product product = productMap.get(input.getProductId());
            if (product == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已删除");
            }
            if (!ProductStatus.ON_SALE.getValue().equals(product.getStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "商品「" + product.getName() + "」已下架，无法购买");
            }
            if (!AuditStatus.APPROVED.getValue().equals(product.getAuditStatus())) {
                throw new BusinessException(ResultCode.CONFLICT, "商品「" + product.getName() + "」未审核通过，无法购买");
            }
            int quantity = input.getQuantity() == null ? 0 : input.getQuantity();
            if (quantity <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "购买数量必须大于 0");
            }
            if (product.getStock() == null || product.getStock() < quantity) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "商品「" + product.getName() + "」库存不足，当前仅剩 "
                                + (product.getStock() == null ? 0 : product.getStock()) + " 件");
            }
            BigDecimal price = product.getPrice();
            if (price == null) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "商品价格异常");
            }
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);

            Long merchantId = product.getMerchantId() == null ? 0L : product.getMerchantId();
            if (!merchantNames.containsKey(merchantId)) {
                User merchant = merchantId == 0L ? null : userMapper.selectById(merchantId);
                String name = merchantId == 0L ? "平台自营"
                        : (merchant == null ? "商家已注销"
                        : (merchant.getNickname() == null || merchant.getNickname().isBlank()
                                ? merchant.getUsername() : merchant.getNickname()));
                merchantNames.put(merchantId, name);
            }

            OrderPreviewVO.PreviewItem item = new OrderPreviewVO.PreviewItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setProductImage(product.getMainImage());
            item.setSpecText("默认规格（单规格商品）");
            item.setUnitPrice(price.setScale(2, RoundingMode.HALF_UP));
            item.setQuantity(quantity);
            item.setSubtotal(subtotal);
            item.setMerchantId(merchantId);
            item.setMerchantName(merchantNames.get(merchantId));
            vo.getItems().add(item);
            total = total.add(subtotal);

            OrderPreviewVO.PreviewMerchantGroup group = groupMap.computeIfAbsent(merchantId, k -> {
                OrderPreviewVO.PreviewMerchantGroup g = new OrderPreviewVO.PreviewMerchantGroup();
                g.setMerchantId(k);
                g.setMerchantName(merchantNames.get(k));
                g.setSubtotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                return g;
            });
            group.getItems().add(item);
            group.setSubtotal(group.getSubtotal().add(subtotal));
        }

        OrderPreviewVO.PreviewAddress addr = new OrderPreviewVO.PreviewAddress();
        addr.setId(address.getId());
        addr.setReceiverName(address.getReceiverName());
        addr.setPhone(address.getPhone());
        addr.setProvince(address.getProvince());
        addr.setCity(address.getCity());
        addr.setDistrict(address.getDistrict());
        addr.setDetailAddress(address.getDetailAddress());
        addr.setIsDefault(address.getIsDefault() != null && address.getIsDefault() == 1);
        vo.setAddress(List.of(addr));
        vo.setMerchantGroups(new ArrayList<>(groupMap.values()));
        vo.setMerchantCount(groupMap.size());
        BigDecimal total2 = total.setScale(2, RoundingMode.HALF_UP);
        vo.setTotalAmount(total2);
        vo.setPayableAmount(total2.subtract(vo.getDiscountAmount()).add(vo.getFreightAmount()));
        return vo;
    }

    /** 解析结算来源：购物车条目 ID 或 商品 ID+数量，两种模式二选一 */
    private List<OrderDraft.DraftLine> resolveLines(Long userId, OrderCreateDTO dto) {
        boolean hasCart = dto.getCartItemIds() != null && !dto.getCartItemIds().isEmpty();
        boolean hasItems = dto.getItems() != null && !dto.getItems().isEmpty();
        if (hasCart == hasItems) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请二选一提交购物车条目或商品列表");
        }

        List<OrderDraft.DraftLine> lines = new ArrayList<>();
        if (hasCart) {
            List<Long> ids = dto.getCartItemIds().stream().distinct().toList();
            List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                    .in(CartItem::getId, ids)
                    .eq(CartItem::getUserId, userId));
            if (items.size() != ids.size()) {
                // 有条目不存在或不属于当前用户 → 404，防止通过 ID 探测他人购物车
                throw new BusinessException(ResultCode.NOT_FOUND, "购物车条目不存在或不属于当前用户");
            }
            for (CartItem item : items) {
                lines.add(new OrderDraft.DraftLine(item.getProductId(), item.getQuantity(), item.getId()));
            }
        } else {
            Set<Long> productIds = dto.getItems().stream()
                    .map(OrderCreateDTO.OrderItemInput::getProductId).collect(Collectors.toSet());
            if (productIds.size() != dto.getItems().size()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "同一商品请勿重复提交");
            }
            for (OrderCreateDTO.OrderItemInput input : dto.getItems()) {
                lines.add(new OrderDraft.DraftLine(input.getProductId(), input.getQuantity(), null));
            }
        }
        if (lines.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "没有可结算的商品");
        }
        return lines;
    }

    @Override
    public PageResult<OrderVO> page(OrderQueryDTO query, long pageNum, long pageSize) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        OrderQueryDTO safe = query == null ? new OrderQueryDTO() : query;
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId);
        if (StringUtils.hasText(safe.getStatus())) {
            // 非法状态不会报错，只是查不到数据；前端状态选项也来自后端枚举
            wrapper.eq(Orders::getStatus, safe.getStatus());
        }
        wrapper.orderByDesc(Orders::getCreatedAt);

        IPage<Orders> page = new Page<>(pageNum, pageSize);
        IPage<Orders> result = orderMapper.selectPage(page, wrapper);
        List<Orders> orders = result.getRecords();

        // 批量取明细，避免逐单查询（N+1）
        Map<Long, List<OrderItem>> itemMap = new HashMap<>();
        if (!orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(Orders::getId).toList();
            for (OrderItem item : orderItemMapper.selectByOrderIds(orderIds)) {
                itemMap.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
            }
        }

        List<OrderVO> vos = orders.stream().map(order -> {
            List<OrderItemVO> items = itemMap.getOrDefault(order.getId(), List.of()).stream()
                    .map(this::toItemVO).toList();
            OrderVO vo = new OrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusDescription(OrderStatus.describe(order.getStatus()));
            vo.setRemark(order.getRemark());
            vo.setCreatedAt(order.getCreatedAt());
            vo.setItems(items);
            vo.setItemCount(items.size());
            if (!items.isEmpty()) {
                vo.setFirstProductName(items.get(0).getProductName());
                vo.setFirstProductImage(items.get(0).getProductImage());
            }
            return vo;
        }).toList();

        return PageResult.of(vos, result.getTotal(), pageNum, pageSize, result.getPages());
    }

    @Override
    public OrderDetailVO detail(Long id) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            // 他人订单与不存在订单统一 404，避免探测
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId));

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDescription(OrderStatus.describe(order.getStatus()));
        vo.setRemark(order.getRemark());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverProvince(order.getReceiverProvince());
        vo.setReceiverCity(order.getReceiverCity());
        vo.setReceiverDistrict(order.getReceiverDistrict());
        vo.setReceiverDetailAddress(order.getReceiverDetailAddress());
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    @Override
    public OrderStatusVO status(Long id) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }

        Set<OrderOperation> operations = OrderStatusMachine.allowedOperations(order.getStatus());
        OrderStatusVO vo = new OrderStatusVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusDescription(OrderStatus.describe(order.getStatus()));
        vo.setAllowedOperations(operations.stream().map(OrderOperation::name).toList());
        vo.setCanPay(operations.contains(OrderOperation.PAY));
        vo.setCanCancel(operations.contains(OrderOperation.CANCEL_BY_USER));
        vo.setCanShip(operations.contains(OrderOperation.SHIP));
        vo.setCanConfirm(operations.contains(OrderOperation.CONFIRM_RECEIPT));
        // 已完成订单可能存在可评价商品；单项是否已评价由评价接口按订单明细校验
        vo.setCanReview(OrderStatus.COMPLETED.getValue().equals(order.getStatus()));
        return vo;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setProductImage(item.getProductImage());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }
}
