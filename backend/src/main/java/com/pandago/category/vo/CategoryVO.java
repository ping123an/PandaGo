package com.pandago.category.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 分类展示对象：仅包含前端需要的字段，不暴露 deleted 等内部字段。 */
@Data
public class CategoryVO {

    private Long id;

    private Long parentId;

    private String name;

    private Integer sort;

    private String icon;

    private Integer status;

    /** 子分类（两级结构） */
    private List<CategoryVO> children = new ArrayList<>();
}
