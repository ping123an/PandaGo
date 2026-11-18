package com.pandago.common.enums;

/** 商品上架状态，与 product.status 字段值一致。 */
public enum ProductStatus {
    ON_SALE("ON_SALE", "上架"),
    OFF_SALE("OFF_SALE", "下架");

    private final String value;
    private final String description;

    ProductStatus(String value, String description) {
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
