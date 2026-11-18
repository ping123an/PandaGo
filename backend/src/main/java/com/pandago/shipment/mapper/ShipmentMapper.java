package com.pandago.shipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.shipment.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {
}
