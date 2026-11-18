package com.pandago.product.controller;

import com.pandago.common.api.PageResult;
import com.pandago.common.api.Result;
import com.pandago.product.dto.ProductCreateDTO;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductStatusDTO;
import com.pandago.product.dto.ProductUpdateDTO;
import com.pandago.product.service.MerchantProductService;
import com.pandago.product.vo.ProductDetailVO;
import com.pandago.product.vo.ProductManagementVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家商品管理接口。
 *
 * <p>Controller 只做参数绑定和基础校验：当前商家 id 由 Service 从 SecurityContext 读取，
 * 请求体中的 merchantId / userId / role 一律不接收。URL 规则与方法级 @PreAuthorize
 * 先挡住普通用户，Service 再做归属校验，形成双保险。</p>
 */
@Validated
@PreAuthorize("hasRole('MERCHANT')")
@RestController
@RequestMapping("/api/v1/merchant/products")
public class MerchantProductController {

    private final MerchantProductService merchantProductService;

    public MerchantProductController(MerchantProductService merchantProductService) {
        this.merchantProductService = merchantProductService;
    }

    /** 我的商品：查询条件由 Service 强制加上当前商家 id */
    @GetMapping
    public Result<PageResult<ProductManagementVO>> list(
            @Valid ProductManagementQueryDTO query,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "每页最小为 1")
            @Max(value = 50, message = "每页最多 50 条") long pageSize) {
        return Result.ok(merchantProductService.listMyProducts(query, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<ProductDetailVO> detail(@PathVariable Long id) {
        return Result.ok(merchantProductService.detail(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductCreateDTO dto) {
        return Result.ok(merchantProductService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateDTO dto) {
        merchantProductService.update(id, dto);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusDTO dto) {
        merchantProductService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    /** 提交审核：PENDING/REJECTED 可提交，APPROVED 返回 409 */
    @PostMapping("/{id}/submit-audit")
    public Result<Void> submitForAudit(@PathVariable Long id) {
        merchantProductService.submitForAudit(id);
        return Result.ok();
    }
}
