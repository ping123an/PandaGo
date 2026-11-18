package com.pandago.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家资料修改请求（阶段 V9）。
 *
 * <p>白名单设计：只有这 6 个可编辑字段；
 * 不含 id / userId / status / auditStatus / role 等敏感字段，
 * 即使客户端额外夹带也会被 Jackson 忽略，后端只 set 白名单字段。</p>
 */
@Data
public class MerchantProfileUpdateDTO {

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 64, message = "店铺名称不能超过 64 个字符")
    private String storeName;

    @Size(max = 500, message = "店铺简介不能超过 500 个字符")
    private String description;

    @Size(max = 32, message = "联系人不能超过 32 个字符")
    private String contactName;

    /** 联系电话：允许留空；非空时校验中国大陆手机号或固话格式 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$",
            message = "联系电话格式不正确（手机号或固话）")
    private String contactPhone;

    @Size(max = 200, message = "店铺地址不能超过 200 个字符")
    private String address;

    @Size(max = 255, message = "店铺 Logo 地址过长")
    private String logo;
}
