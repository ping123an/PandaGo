package com.pandago.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员商品审核请求（阶段 4）。 */
@Data
public class ProductAuditDTO {

    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "APPROVED|REJECTED", message = "审核结果只能为 APPROVED 或 REJECTED")
    private String auditStatus;

    @Size(max = 255, message = "驳回原因最长 255 个字符")
    private String reason;
}
