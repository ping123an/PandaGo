package com.pandago.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.merchant.entity.MerchantProfile;
import org.apache.ibatis.annotations.Mapper;

/** 商家店铺资料 Mapper：单表 CRUD 用 MyBatis-Plus BaseMapper。 */
@Mapper
public interface MerchantProfileMapper extends BaseMapper<MerchantProfile> {
}
