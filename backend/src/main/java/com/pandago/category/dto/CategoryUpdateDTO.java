package com.pandago.category.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改分类请求。status 用 1 启用 / 0 禁用。 */
@Data
public class CategoryUpdateDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 个字符")
    private String name;

    @Min(value = 0, message = "排序值不能为负数")
    @Max(value = 9999, message = "排序值过大")
    private Integer sort;

    @Size(max = 100, message = "图标地址过长")
    private String icon;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
