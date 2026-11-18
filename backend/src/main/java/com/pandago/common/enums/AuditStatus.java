package com.pandago.common.enums;

/** 商品审核状态，审核流程在阶段 4 实现，阶段 2 仅保存初始值。 */
public enum AuditStatus {
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "审核通过"),
    REJECTED("REJECTED", "审核驳回");

    private final String value;
    private final String description;

    AuditStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
