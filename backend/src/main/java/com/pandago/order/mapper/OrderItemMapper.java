package com.pandago.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /** 按订单 ID 批量取明细，避免逐单查询造成 N+1 */
    List<OrderItem> selectByOrderIds(@Param("orderIds") List<Long> orderIds);
}
