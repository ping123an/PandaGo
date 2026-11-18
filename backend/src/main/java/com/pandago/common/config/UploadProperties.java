package com.pandago.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 本地上传配置。当前仅头像使用；商品图片仍沿用 URL 白名单方案，避免扩大改造范围。
 *
 * <p>baseDir 是相对进程工作目录的根目录，默认 uploads；头像固定写入其下的 avatars/ 子目录。
 * 生产环境可通过环境变量 PANDAGO_UPLOAD_BASE_DIR 指向持久化目录。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "pandago.upload")
public class UploadProperties {

    private String baseDir = "uploads";

    /** 单个头像最大字节数；Spring multipart 上限需略大于此值 */
    private long maxAvatarBytes = 2L * 1024 * 1024;

    public Path baseDirPath() {
        return Path.of(baseDir).toAbsolutePath().normalize();
    }

    public Path avatarDirPath() {
        return baseDirPath().resolve("avatars").normalize();
    }
}
