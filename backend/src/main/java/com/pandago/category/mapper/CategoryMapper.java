package com.pandago.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.category.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
