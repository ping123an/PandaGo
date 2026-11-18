package com.pandago.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.favorite.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
