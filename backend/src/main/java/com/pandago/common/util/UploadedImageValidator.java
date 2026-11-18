package com.pandago.common.util;

import com.pandago.common.api.ResultCode;
import com.pandago.common.config.UploadProperties;
import com.pandago.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * 上传图片校验：扩展名 + Content-Type + 文件头魔数三重校验。
 *
 * <p>只依赖前端校验是不够的：攻击者可以改请求、伪造 Content-Type、把 exe 改名为 .png。
 * 这里读取实际文件头判断真实类型，并要求扩展名、Content-Type、魔数三者一致；
 * 不允许 SVG（可能携带脚本），只允许 JPG/JPEG/PNG/WEBP。</p>
 */
@Component
@RequiredArgsConstructor
public class UploadedImageValidator {

    private static final Map<String, String> EXPECTED_CONTENT_TYPES = Map.of(
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    private final UploadProperties uploadProperties;

    /** 校验通过返回规范化后的扩展名（不含点），可直接用于生成随机文件名 */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getSize() > uploadProperties.getMaxAvatarBytes()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "头像图片不能超过 " + (uploadProperties.getMaxAvatarBytes() / 1024 / 1024) + "MB");
        }

        String extension = extensionOf(file.getOriginalFilename());
        String normalized = normalizeExtension(extension);
        String expectedType = EXPECTED_CONTENT_TYPES.get(normalized);
        if (expectedType == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "头像只支持 JPG、JPEG、PNG、WEBP 格式");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!expectedType.equals(contentType) && !(normalized.equals("jpeg") && contentType.equals("image/jpg"))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片类型与文件格式不一致");
        }

        byte[] head;
        try {
            head = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "读取上传图片失败");
        }
        String detected = detectByMagic(head);
        if (detected == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件内容不是有效的 JPG、PNG 或 WEBP 图片");
        }
        if (!detected.equals(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件扩展名与实际图片内容不一致");
        }
        return normalized;
    }

    /** 从原始文件名取扩展名；忽略路径部分，避免路径穿越 */
    private String extensionOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件缺少文件名");
        }
        String name = originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "上传文件缺少扩展名");
        }
        return name.substring(dot + 1);
    }

    private String normalizeExtension(String extension) {
        String value = extension.toLowerCase(Locale.ROOT);
        return value.equals("jpg") ? "jpeg" : value;
    }

    /** 根据文件头识别真实图片类型，无法识别返回 null */
    private String detectByMagic(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (startsWith(bytes, png)) {
            return "png";
        }
        if (bytes.length >= 12
                && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
            return "webp";
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
