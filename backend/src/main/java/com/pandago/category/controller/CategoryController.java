package com.pandago.category.controller;

import com.pandago.category.dto.CategoryCreateDTO;
import com.pandago.category.dto.CategoryUpdateDTO;
import com.pandago.category.service.CategoryService;
import com.pandago.category.vo.CategoryVO;
import com.pandago.common.api.Result;
import jakarta.validation.Valid;
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

/** 分类接口：用户端只读，管理端写操作走 /api/v1/admin 并由开发期保护拦截。 */
@Validated
@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** 用户端：启用的两级分类 */
    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.ok(categoryService.listEnabledGrouped());
    }

    /** 管理端：含禁用分类 */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/categories")
    public Result<List<CategoryVO>> listAllCategories() {
        return Result.ok(categoryService.listAllGrouped());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/categories")
    public Result<Long> create(@Valid @RequestBody CategoryCreateDTO dto) {
        return Result.ok(categoryService.create(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/categories/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateDTO dto) {
        categoryService.update(id, dto);
        return Result.ok();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/categories/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam Integer status) {
        categoryService.updateStatus(id, status);
        return Result.ok();
    }
}
