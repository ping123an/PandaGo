package com.pandago.merchant.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家店铺资料 VO。
 *
 * <p>不返回 user_id 之外的敏感信息（不含密码、角色等）；
 * user_id 仅作为店铺所属关系展示，非敏感。</p>
 */
@Data
public class MerchantProfileVO {

    private Long id;

    /** 店铺所属用户 ID（只读展示） */
    private Long userId;

    private String storeName;

    private String logo;

    private String description;

    private String contactName;

    private String contactPhone;

    private String address;

    /** 经营状态：NORMAL / SUSPENDED */
    private String status;

    private String statusDescription;

    /** 审核状态：PENDING / APPROVED / REJECTED */
    private String auditStatus;

    private String auditStatusDescription;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
