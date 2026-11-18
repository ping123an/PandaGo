package com.pandago.auth.service;

import com.pandago.auth.vo.AvatarUploadVO;
import org.springframework.web.multipart.MultipartFile;

/** 当前登录用户头像上传服务。 */
public interface UserAvatarService {

    /** 校验、保存图片并把 URL 写入当前用户；拒绝任何他人身份参数 */
    AvatarUploadVO upload(MultipartFile file);
}
