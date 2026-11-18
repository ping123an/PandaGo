package com.pandago.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;

/**
 * 上传目录静态资源映射：/uploads/** -> {baseDir}/**。
 *
 * <p>头像文件不进数据库，只保存 URL；前端与图片直链都通过 /uploads/avatars/xxx.webp 访问。
 * SecurityConfig 已把 /uploads/** 设为 permitAll，因为它只用于展示图片。</p>
 */
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @PostConstruct
    public void ensureDirectories() throws IOException {
        Files.createDirectories(uploadProperties.avatarDirPath());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Files.createDirectories(uploadProperties.avatarDirPath());
        } catch (IOException e) {
            throw new IllegalStateException("无法创建头像上传目录", e);
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadProperties.avatarDirPath().getParent().toUri().toString());
    }
}
