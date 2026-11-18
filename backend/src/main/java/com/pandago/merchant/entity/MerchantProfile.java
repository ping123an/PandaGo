package com.pandago.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家店铺资料实体（V9）。
 *
 * <p>本项目商家 ID 即用户 ID，店铺资料与 user 表一对一，user_id 唯一。
 * status / audit_status 由平台控制，商家资料接口不得修改。</p>
 */
@Data
@TableName("merchant_profile")
public class MerchantProfile {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商家用户 ID，与 product.merchant_id / orders.merchant_id 一致 */
    private Long userId;

    private String storeName;

    private String logo;

    private String description;

    private String contactName;

    private String contactPhone;

    private String address;

    /** 经营状态：NORMAL / SUSPENDED */
    private String status;

    /** 审核状态：PENDING / APPROVED / REJECTED */
    private String auditStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
