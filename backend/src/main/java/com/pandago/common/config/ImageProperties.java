package com.pandago.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图片 URL 配置（阶段 4）。
 *
 * <p>本项目尚未接入对象存储，因此只接受「本地开发静态资源路径」或「允许的 http(s) URL」。
 * 真实文件流的类型与字节大小校验应放在未来的上传接口中；本阶段校验 URL 形态、
 * 扩展名和请求中声明的 sizeBytes，避免先把明显非法的图片记录写进数据库。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "pandago.image")
public class ImageProperties {

    /** 允许的图片地址前缀；本地路径以 /images/ 开头，其余必须是 http(s) */
    private List<String> allowedUrlPrefixes = List.of("/images/", "http://", "https://");

    /** 允许的图片扩展名，按 URL path 判断，不信任客户端 Content-Type */
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp", "gif", "svg");

    /** URL 最大长度，与 product_image.url / product.main_image 的 VARCHAR(255) 对齐 */
    private int maxUrlLength = 255;

    /** 请求元数据中声明的图片最大字节数；仅作前置拒绝，不等同于服务端读取文件校验 */
    private long maxImageBytes = 5L * 1024 * 1024;
}
