package com.pandago.merchant.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 商家店铺头像上传响应：只包含可公开访问的相对 URL。 */
@Data
@AllArgsConstructor
public class MerchantLogoUploadVO {
    private String logoUrl;
}
