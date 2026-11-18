package com.pandago.merchant.controller;

import com.pandago.common.api.Result;
import com.pandago.merchant.dto.MerchantProfileUpdateDTO;
import com.pandago.merchant.service.MerchantProfileService;
import com.pandago.merchant.vo.MerchantLogoUploadVO;
import com.pandago.merchant.vo.MerchantProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商家店铺资料接口（V9）。
 *
 * <p>只能查询/修改当前登录商家自己的资料；请求体只含白名单字段，
 * 店铺 ID / 所属用户 / 经营状态 / 审核状态不接受前端传值。</p>
 */
@Validated
@PreAuthorize("hasRole('MERCHANT')")
@RestController
@RequestMapping("/api/v1/merchant/profile")
@RequiredArgsConstructor
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    /** 查询我的店铺资料 */
    @GetMapping
    public Result<MerchantProfileVO> getMyProfile() {
        return Result.ok(merchantProfileService.getMyProfile());
    }

    /** 修改我的店铺资料（白名单字段） */
    @PutMapping
    public Result<MerchantProfileVO> updateMyProfile(@Valid @RequestBody MerchantProfileUpdateDTO dto) {
        return Result.ok(merchantProfileService.updateMyProfile(dto));
    }

    /** 上传店铺头像：multipart/form-data，字段名固定为 file；身份由 JWT 解析 */
    @PostMapping("/logo")
    public Result<MerchantLogoUploadVO> uploadLogo(@RequestParam("file") MultipartFile file) {
        return Result.ok(merchantProfileService.uploadLogo(file));
    }
}
