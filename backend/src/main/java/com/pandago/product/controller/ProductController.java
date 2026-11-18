package com.pandago.product.controller;

import com.pandago.common.api.PageResult;
import com.pandago.common.api.Result;
import com.pandago.product.dto.ProductAuditDTO;
import com.pandago.product.dto.ProductCreateDTO;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductQueryDTO;
import com.pandago.product.dto.ProductStatusDTO;
import com.pandago.product.dto.ProductUpdateDTO;
import com.pandago.product.service.ProductService;
import com.pandago.product.vo.ProductDetailVO;
import com.pandago.product.vo.ProductListVO;
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

import java.util.List;

/** 商品接口：用户端浏览 + 管理端基础维护与审核。 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 用户端分页列表；分页参数在 Controller 层限制范围，排序字段在 Service 层白名单校验。 */
    @GetMapping("/products")
    public Result<PageResult<ProductListVO>> page(
            @Valid ProductQueryDTO query,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") long pageNum,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "每页最小为 1")
            @Max(value = 50, message = "每页最多 50 条") long pageSize) {
        return Result.ok(productService.page(query, pageNum, pageSize));
    }

    /** 首页热门推荐，数量由后端限制，避免一次拉取过多数据。 */
    @GetMapping("/products/hot")
    public Result<List<ProductListVO>> hot(
            @RequestParam(defaultValue = "4") @Min(value = 1, message = "至少 1 条")
            @Max(value = 20, message = "最多 20 条") int limit) {
        return Result.ok(productService.hot(limit));
    }

    @GetMapping("/products/{id}")
    public Result<ProductDetailVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/products")
    public Result<PageResult<ProductManagementVO>> adminPage(
            @Valid ProductManagementQueryDTO query,
            @RequestParam(defaultValue = "1") @Min(value = 1) long pageNum,
            @RequestParam(defaultValue = "12") @Min(value = 1) @Max(value = 50) long pageSize) {
        return Result.ok(productService.adminPage(query, pageNum, pageSize));
    }

    /** 管理端：可查看未上架/待审核商品 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/products/{id}")
    public Result<ProductDetailVO> adminDetail(@PathVariable Long id) {
        return Result.ok(productService.adminDetail(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products")
    public Result<Long> create(@Valid @RequestBody ProductCreateDTO dto) {
        return Result.ok(productService.create(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/products/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateDTO dto) {
        productService.update(id, dto);
        return Result.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/products/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusDTO dto) {
        productService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/products/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody ProductAuditDTO dto) {
        productService.audit(id, dto);
        return Result.ok();
    }
}
