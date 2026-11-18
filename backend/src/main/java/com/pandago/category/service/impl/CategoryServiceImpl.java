package com.pandago.category.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.pandago.cache.CacheKeys;
import com.pandago.cache.CacheService;
import com.pandago.category.dto.CategoryCreateDTO;
import com.pandago.category.dto.CategoryUpdateDTO;
import com.pandago.category.entity.Category;
import com.pandago.category.mapper.CategoryMapper;
import com.pandago.category.service.CategoryService;
import com.pandago.category.vo.CategoryVO;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.CommonStatus;
import com.pandago.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 分类业务：列表组装两级树、增删改与启停校验。
 *
 * <p>Controller 不参与树结构组装和归属校验；这里统一处理业务规则，
 * 数据访问只通过 CategoryMapper。</p>
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final CacheService cacheService;

    public CategoryServiceImpl(CategoryMapper categoryMapper, CacheService cacheService) {
        this.categoryMapper = categoryMapper;
        this.cacheService = cacheService;
    }

    @Override
    public List<CategoryVO> listEnabledGrouped() {
        String key = CacheKeys.CATEGORY_LIST;
        Optional<List<CategoryVO>> cached = cacheService.get(key, new TypeReference<List<CategoryVO>>() {});
        if (cached.isPresent()) {
            return cached.get();
        }
        List<CategoryVO> tree = buildTree(categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, CommonStatus.ENABLED.getValue())
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId)));
        cacheService.set(key, tree, CacheKeys.CATEGORY_TTL);
        return tree;
    }

    @Override
    public List<CategoryVO> listAllGrouped() {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
        return buildTree(categories);
    }

    @Override
    public Long create(CategoryCreateDTO dto) {
        long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "父分类不存在");
            }
            if (parent.getStatus() == null || parent.getStatus() != CommonStatus.ENABLED.getValue()) {
                throw new BusinessException(ResultCode.CONFLICT, "父分类已禁用，不能新增子分类");
            }
        }
        ensureNameAvailable(parentId, dto.getName(), null);

        Category category = new Category();
        category.setParentId(parentId);
        category.setName(dto.getName().trim());
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
        category.setIcon(dto.getIcon());
        category.setStatus(CommonStatus.ENABLED.getValue());
        categoryMapper.insert(category);
        cacheService.evict(CacheKeys.CATEGORY_LIST);
        return category.getId();
    }

    @Override
    public void update(Long id, CategoryUpdateDTO dto) {
        Category category = requireCategory(id);
        ensureNameAvailable(category.getParentId(), dto.getName(), id);

        category.setName(dto.getName().trim());
        category.setSort(dto.getSort());
        category.setIcon(dto.getIcon());
        category.setStatus(dto.getStatus());
        categoryMapper.updateById(category);
        cacheService.evict(CacheKeys.CATEGORY_LIST);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Category category = requireCategory(id);
        category.setStatus(status);
        categoryMapper.updateById(category);
        cacheService.evict(CacheKeys.CATEGORY_LIST);
    }

    /** 两级树组装：先放一级分类，再把子分类挂到对应父节点下。 */
    private List<CategoryVO> buildTree(List<Category> categories) {
        Map<Long, CategoryVO> roots = new LinkedHashMap<>();
        for (Category category : categories) {
            if (category.getParentId() == null || category.getParentId() == 0L) {
                roots.put(category.getId(), toVO(category));
            }
        }
        for (Category category : categories) {
            if (category.getParentId() != null && category.getParentId() != 0L) {
                CategoryVO parent = roots.get(category.getParentId());
                if (parent != null) {
                    parent.getChildren().add(toVO(category));
                }
            }
        }
        return new ArrayList<>(roots.values());
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setParentId(category.getParentId());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        vo.setIcon(category.getIcon());
        vo.setStatus(category.getStatus());
        return vo;
    }

    private Category requireCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    /** 同一父分类下不允许重名，先做友好校验；数据库唯一索引是最终保障。 */
    private void ensureNameAvailable(Long parentId, String name, Long excludeId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, parentId)
                .eq(Category::getName, name.trim())
                .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "同一父分类下分类名称已存在");
        }
    }
}
