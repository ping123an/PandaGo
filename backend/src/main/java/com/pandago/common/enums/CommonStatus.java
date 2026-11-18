package com.pandago.common.enums;

/** 通用启用状态：category.status、user.status 使用。 */
public enum CommonStatus {
    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final int value;
    private final String description;

    CommonStatus(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
