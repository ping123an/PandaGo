package com.pandago.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pandago.admin.service.AdminOrderService;
import com.pandago.common.api.PageResult;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderItemVO;
import com.pandago.order.vo.OrderVO;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理端平台订单只读查询：全部方法先做 ADMIN 角色校验。 */
@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public PageResult<OrderVO> page(String orderNo, String status, long pageNum, long pageSize) {
        SecurityUtils.requireRole(RoleCode.ADMIN);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .like(StringUtils.hasText(orderNo), Orders::getOrderNo, orderNo)
                .eq(StringUtils.hasText(status), Orders::getStatus, status)
                .orderByDesc(Orders::getCreatedAt);
        IPage<Orders> result = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<Long, List<OrderItem>> itemMap = new HashMap<>();
        List<Orders> orders = result.getRecords();
        if (!orders.isEmpty()) {
            for (OrderItem item : orderItemMapper.selectByOrderIds(
                    orders.stream().map(Orders::getId).toList())) {
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
        SecurityUtils.requireRole(RoleCode.ADMIN);
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new com.pandago.common.exception.BusinessException(
                    com.pandago.common.api.ResultCode.NOT_FOUND, "订单不存在");
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
