package com.pandago.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 商家发货请求（阶段 8）：只接收物流公司与单号，订单 ID 走路径参数，状态由后端判定。 */
@Data
public class ShipmentDTO {

    @NotBlank(message = "物流公司不能为空")
    @Size(max = 50, message = "物流公司最长 50 个字符")
    private String company;

    @NotBlank(message = "物流单号不能为空")
    @Size(min = 4, max = 64, message = "物流单号长度应在 4~64 个字符")
    private String trackingNo;
}
