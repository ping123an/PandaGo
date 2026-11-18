package com.pandago.shipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.security.SecurityUtils;
import com.pandago.shipment.entity.Shipment;
import com.pandago.shipment.mapper.ShipmentMapper;
import com.pandago.shipment.service.ShipmentQueryService;
import com.pandago.shipment.vo.ShipmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户端物流查询实现（阶段 8）。
 *
 * <p>先校验订单归属（他人/不存在订单统一 404 防探测），再读物流。
 * 只返回公司与单号，不返回任何轨迹数据。</p>
 */
@Service
@RequiredArgsConstructor
public class ShipmentQueryServiceImpl implements ShipmentQueryService {

    private final OrderMapper orderMapper;
    private final ShipmentMapper shipmentMapper;

    @Override
    public ShipmentVO mineByOrderId(Long orderId) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }
        Shipment shipment = shipmentMapper.selectOne(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getOrderId, order.getId()));
        if (shipment == null) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "订单尚未发货（当前状态：" + OrderStatus.describe(order.getStatus()) + "）");
        }
        ShipmentVO vo = new ShipmentVO();
        vo.setId(shipment.getId());
        vo.setOrderId(shipment.getOrderId());
        vo.setOrderNo(shipment.getOrderNo());
        vo.setCompany(shipment.getCompany());
        vo.setTrackingNo(shipment.getTrackingNo());
        vo.setShippedAt(shipment.getShippedAt());
        return vo;
    }
}
