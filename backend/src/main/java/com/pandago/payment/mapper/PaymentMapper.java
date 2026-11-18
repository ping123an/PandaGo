package com.pandago.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentMapper extends BaseMapper<PaymentRecord> {

    /** 按订单号 + 用户查询，查询也带上归属条件，避免泄露他人支付结果。 */
    @Select("SELECT * FROM payment_record WHERE order_no = #{orderNo} AND user_id = #{userId} LIMIT 1")
    PaymentRecord selectByOrderNoAndUser(@Param("orderNo") String orderNo, @Param("userId") Long userId);
}
