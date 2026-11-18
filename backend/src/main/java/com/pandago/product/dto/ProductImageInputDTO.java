package com.pandago.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商品详情图输入（阶段 4）。
 *
 * <p>本阶段没有文件上传，字段保存的是图片 URL；sizeBytes 是请求声明的字节数，
 * 只用于前置拒绝明显超限的数据，服务端不会把它当成真实文件大小。</p>
 */
@Data
public class ProductImageInputDTO {

    @NotBlank(message = "详情图地址不能为空")
    @Size(max = 255, message = "详情图地址过长")
    private String url;

    /** 必须是 DETAIL；主图使用 ProductCreateDTO / ProductUpdateDTO 的 mainImage 字段 */
    @NotBlank(message = "图片类型不能为空")
    @Pattern(regexp = "MAIN|DETAIL", message = "图片类型只能为 MAIN 或 DETAIL")
    private String type;

    /** 请求声明的字节数，可选；真正的字节大小需在未来上传接口读取文件流后校验 */
    @Min(value = 0, message = "图片声明大小不能为负数")
    private Long sizeBytes;
}
