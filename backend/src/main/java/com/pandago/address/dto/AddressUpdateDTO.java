package com.pandago.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改收货地址请求（全量更新除 isDefault 外的字段）。 */
@Data
public class AddressUpdateDTO {

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50, message = "收货人姓名不能超过 50 个字符")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "省份不能为空")
    @Size(max = 50, message = "省份不能超过 50 个字符")
    private String province;

    @NotBlank(message = "城市不能为空")
    @Size(max = 50, message = "城市不能超过 50 个字符")
    private String city;

    @NotBlank(message = "区县不能为空")
    @Size(max = 50, message = "区县不能超过 50 个字符")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址不能超过 200 个字符")
    private String detailAddress;

    /** 是否设为默认；不传表示保持原状，传 false 表示取消该地址的默认标记 */
    private Boolean isDefault;
}
