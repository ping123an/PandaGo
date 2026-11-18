package com.pandago.common.util;

import com.pandago.common.api.ResultCode;
import com.pandago.common.config.ImageProperties;
import com.pandago.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * 商品图片 URL 校验器。
 *
 * <p>当前没有对象存储或上传接口，所以这里不声称完成了「真实文件内容校验」。
 * 本阶段做的是可验证的前置约束：白名单前缀、协议/主机、扩展名、URL 长度、
 * 路径穿越和请求声明的字节大小。文件字节流的真实类型与大小需等上传接口实现后再校验。</p>
 */
@Component
@RequiredArgsConstructor
public class ImageUrlValidator {

    private final ImageProperties properties;

    /**
     * 校验并返回去除首尾空白的 URL。
     *
     * @param rawUrl    客户端提交的地址
     * @param fieldName 字段名，用于错误提示
     * @param sizeBytes 请求声明的字节数；为 null 时跳过大小检查
     */
    public String validate(String rawUrl, String fieldName, Long sizeBytes) {
        String url = rawUrl == null ? null : rawUrl.trim();
        if (url == null || url.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "不能为空");
        }
        if (url.length() > properties.getMaxUrlLength()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    fieldName + "不能超过 " + properties.getMaxUrlLength() + " 个字符");
        }
        if (url.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "不能包含空白字符");
        }
        if (url.contains("..")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "不能包含路径穿越字符");
        }

        boolean allowedPrefix = properties.getAllowedUrlPrefixes().stream().anyMatch(url::startsWith);
        if (!allowedPrefix) {
            throw new BusinessException(ResultCode.BAD_REQUEST, fieldName + "地址前缀不在白名单内");
        }

        String path = extractPath(url);
        String extension = extensionOf(path);
        if (extension == null || !properties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    fieldName + "只支持 " + String.join(" / ", properties.getAllowedExtensions()) + " 格式");
        }

        if (sizeBytes != null && sizeBytes > properties.getMaxImageBytes()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    fieldName + "声明大小不能超过 " + properties.getMaxImageBytes() + " 字节");
        }
        return url;
    }

    private String extractPath(String url) {
        if (url.startsWith("/")) {
            return url.split("[?#]", 2)[0];
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址只支持 http/https 或本地 /images/ 路径");
            }
            if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址主机不合法");
            }
            return uri.getPath() == null ? "" : uri.getPath();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片地址格式不合法");
        }
    }

    private String extensionOf(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash + 1 || dot == path.length() - 1) {
            return null;
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
