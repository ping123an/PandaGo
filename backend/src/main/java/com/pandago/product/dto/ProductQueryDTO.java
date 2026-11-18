package com.pandago.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表查询条件。
 *
 * <p>sortColumn / sortDirection 是内部字段：Service 会依据前端传入的 sort / order
 * 通过白名单重新赋值。未知排序字段直接返回 400，绝不把前端原始字符串用于 SQL。</p>
 */
@Data
public class ProductQueryDTO {

    /** 关键词，匹配商品名与副标题；拒绝 SQL LIKE 通配符和特殊字符 */
    @Size(max = 50, message = "搜索关键词最长 50 个字符")
    @Pattern(regexp = "[\\p{L}\\p{N}\\s-]*", message = "关键词只能包含中文、字母、数字、空格和连字符")
    private String keyword;

    /** 分类 ID（按一级/二级分类筛选） */
    private Long categoryId;

    @DecimalMin(value = "0", message = "最低价格不能为负数")
    private BigDecimal minPrice;

    @DecimalMin(value = "0", message = "最高价格不能为负数")
    private BigDecimal maxPrice;

    /** 排序字段标识：default / price / sales / newest */
    private String sort = "default";

    /** 排序方向：asc / desc */
    private String order = "desc";

    /** 内部字段：经白名单校验后的真实 SQL 排序列 */
    private String sortColumn = "p.sort";

    /** 内部字段：经白名单校验后的排序方向 */
    private String sortDirection = "DESC";
}
