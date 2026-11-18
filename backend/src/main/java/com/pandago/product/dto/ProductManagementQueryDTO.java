package com.pandago.product.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商品管理列表查询条件，供商家「我的商品」和管理员审核列表共用。
 *
 * <p>merchantId 是服务端内部字段：商家端由 SecurityUtils 写入当前登录用户 id，
 * 管理员端保持 null（表示查询全平台）。客户端不能通过请求参数改变它。</p>
 */
@Data
public class ProductManagementQueryDTO {

    /** 商品名称关键词 */
    @Size(max = 50, message = "搜索关键词最长 50 个字符")
    @Pattern(regexp = "[\\p{L}\\p{N}\\s-]*", message = "关键词只能包含中文、字母、数字、空格和连字符")
    private String keyword;

    /** 商品分类 ID（可选，管理端分类筛选） */
    private Long categoryId;

    /** ON_SALE / OFF_SALE */
    @Pattern(regexp = "ON_SALE|OFF_SALE", message = "上架状态不合法")
    private String status;

    /** PENDING / APPROVED / REJECTED */
    @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "审核状态不合法")
    private String auditStatus;

    /** 服务端注入：商家端只查本人商品；管理员端为 null */
    private Long merchantId;

    /** 排序标识：default / price / sales / newest */
    private String sort = "default";

    /** asc / desc */
    private String order = "desc";

    /** 内部字段：白名单校验后的 SQL 排序列 */
    private String sortColumn = "p.id";

    /** 内部字段：白名单校验后的排序方向 */
    private String sortDirection = "DESC";
}
