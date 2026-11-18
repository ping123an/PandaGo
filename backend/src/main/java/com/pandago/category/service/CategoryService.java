package com.pandago.category.service;

import com.pandago.category.dto.CategoryCreateDTO;
import com.pandago.category.dto.CategoryUpdateDTO;
import com.pandago.category.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    /** 用户端：按两级结构返回启用分类 */
    List<CategoryVO> listEnabledGrouped();

    /** 管理端：返回全部分类（含禁用），同样按两级结构 */
    List<CategoryVO> listAllGrouped();

    Long create(CategoryCreateDTO dto);

    void update(Long id, CategoryUpdateDTO dto);

    /** 启用或禁用分类：status 仅接受 0 / 1 */
    void updateStatus(Long id, Integer status);
}
