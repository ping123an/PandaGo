package com.pandago.address.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 收货地址展示对象，不暴露内部字段与 user_id 之外的敏感信息。 */
@Data
public class AddressVO {

    private Long id;

    private String receiverName;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    private Boolean isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
