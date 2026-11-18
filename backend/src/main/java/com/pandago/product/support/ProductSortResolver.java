package com.pandago.product.support;

import com.pandago.common.api.ResultCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductQueryDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 排序白名单解析器。
 *
 * <p>Mapper XML 的 ORDER BY 必须使用 ${} 才能传入列名和方向，因此这里的规则很关键：
 * 前端只能传 default / price / sales / newest 与 asc / desc，解析器把它们映射为固定 SQL 片段。
 * 未知值直接抛 400，不能静默拼接到 SQL。</p>
 */
@Component
public class ProductSortResolver {

    private static final Map<String, String> USER_SORT_COLUMNS = Map.of(
            "default", "p.sort",
            "price", "p.price",
            "sales", "p.sales",
            "newest", "p.created_at");

    private static final Map<String, String> MANAGEMENT_SORT_COLUMNS = Map.of(
            "default", "p.id",
            "price", "p.price",
            "sales", "p.sales",
            "newest", "p.created_at");

    public void apply(ProductQueryDTO query) {
        String key = normalizeKey(query.getSort());
        String column = USER_SORT_COLUMNS.get(key);
        if (column == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的排序字段");
        }
        query.setSortColumn(column);
        query.setSortDirection(resolveDirection(query.getOrder()));
    }

    public void apply(ProductManagementQueryDTO query) {
        String key = normalizeKey(query.getSort());
        String column = MANAGEMENT_SORT_COLUMNS.get(key);
        if (column == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的排序字段");
        }
        query.setSortColumn(column);
        query.setSortDirection(resolveDirection(query.getOrder()));
    }

    private String normalizeKey(String key) {
        return key == null || key.isBlank() ? "default" : key.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String resolveDirection(String order) {
        if (order == null || order.isBlank()) {
            return "DESC";
        }
        if ("asc".equalsIgnoreCase(order.trim())) {
            return "ASC";
        }
        if ("desc".equalsIgnoreCase(order.trim())) {
            return "DESC";
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "排序方向只能为 asc 或 desc");
    }
}
