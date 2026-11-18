package com.pandago.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商品评价请求（阶段 8）。
 *
 * <p>只接收订单明细 ID、评分与内容；用户归属、订单是否完成、商品归属均由后端按
 * orderItemId 反查订单校验，不接受前端传 userId / orderId / productId。</p>
 */
@Data
public class ReviewCreateDTO {

    @NotNull(message = "订单明细ID不能为空")
    private Long orderItemId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为 1")
    @Max(value = 5, message = "评分最大为 5")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    @Size(min = 5, max = 500, message = "评价内容长度应在 5~500 个字符")
    private String content;
}
