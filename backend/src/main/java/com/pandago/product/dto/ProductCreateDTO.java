package com.pandago.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 新增商品请求。审核状态、商家 ID 由后端固定，不接受前端传值。 */
@Data
public class ProductCreateDTO {

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 150, message = "商品名称最长 150 个字符")
    private String name;

    @Size(max = 255, message = "商品简介过长")
    private String subtitle;

    @NotBlank(message = "商品主图不能为空")
    @Size(max = 255, message = "主图地址过长")
    private String mainImage;

    /** 请求声明的主图字节数，可选；真正的文件大小需未来上传接口读取文件流校验 */
    @Min(value = 0, message = "主图声明大小不能为负数")
    private Long mainImageSizeBytes;

    @Valid
    @Size(max = 9, message = "详情图最多 9 张")
    private List<ProductImageInputDTO> detailImages = new ArrayList<>();

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    @Size(max = 4000, message = "商品详情过长")
    private String description;

    @Min(value = 0, message = "排序值不能为负数")
    private Integer sort = 0;
}
