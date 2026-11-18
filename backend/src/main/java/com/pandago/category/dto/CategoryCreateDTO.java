package com.pandago.category.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新增分类请求。parentId 为 0 表示一级分类。 */
@Data
public class CategoryCreateDTO {

    @Min(value = 0, message = "parentId 不能为负数")
    private Long parentId = 0L;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 个字符")
    private String name;

    @Min(value = 0, message = "排序值不能为负数")
    @Max(value = 9999, message = "排序值过大")
    private Integer sort = 0;

    @Size(max = 100, message = "图标地址过长")
    private String icon;
}
