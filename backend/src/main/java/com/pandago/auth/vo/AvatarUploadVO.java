package com.pandago.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 头像上传响应：只包含可公开访问的相对 URL，不含磁盘绝对路径或用户信息。 */
@Data
@AllArgsConstructor
public class AvatarUploadVO {
    private String avatarUrl;
}
