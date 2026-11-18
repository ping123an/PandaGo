package com.pandago.merchant.service;

import com.pandago.merchant.dto.MerchantProfileUpdateDTO;
import com.pandago.merchant.vo.MerchantLogoUploadVO;
import com.pandago.merchant.vo.MerchantProfileVO;
import org.springframework.web.multipart.MultipartFile;

/** 商家店铺资料业务入口：只允许操作当前登录商家自己的资料。 */
public interface MerchantProfileService {

    /** 查询当前登录商家的店铺资料 */
    MerchantProfileVO getMyProfile();

    /** 修改当前登录商家的店铺资料（白名单字段） */
    MerchantProfileVO updateMyProfile(MerchantProfileUpdateDTO dto);

    /** 上传当前登录商家的店铺头像：校验图片后写库到 merchant_profile.logo */
    MerchantLogoUploadVO uploadLogo(MultipartFile file);
}
