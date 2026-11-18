package com.pandago.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.config.UploadProperties;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.common.service.PhoneLinkService;
import com.pandago.common.util.UploadedImageValidator;
import com.pandago.merchant.dto.MerchantProfileUpdateDTO;
import com.pandago.merchant.entity.MerchantProfile;
import com.pandago.merchant.mapper.MerchantProfileMapper;
import com.pandago.merchant.service.MerchantProfileService;
import com.pandago.merchant.vo.MerchantLogoUploadVO;
import com.pandago.merchant.vo.MerchantProfileVO;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 商家店铺资料业务实现（V9）。
 *
 * <p>安全要点：
 * 1. 所有方法先校验 MERCHANT 角色并从 SecurityContext 读 userId，不接受前端 merchantId；
 * 2. 更新只用 DTO 白名单字段 set，id / userId / status / auditStatus 永远不会写库；
 * 3. 若该商家尚无资料（迁移后新建的商家账号），首次查询自动创建一份默认资料。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantProfileServiceImpl implements MerchantProfileService {

    private final MerchantProfileMapper profileMapper;
    private final UserMapper userMapper;
    private final PhoneLinkService phoneLinkService;
    private final UploadedImageValidator imageValidator;
    private final UploadProperties uploadProperties;

    @Override
    public MerchantProfileVO getMyProfile() {
        MerchantProfile profile = requireMyProfile();
        return toVO(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantProfileVO updateMyProfile(MerchantProfileUpdateDTO dto) {
        Long userId = currentMerchantId();
        MerchantProfile profile = requireMyProfile();
        String contactPhone = blankToNull(dto.getContactPhone());

        // 只更新白名单字段；店铺 ID / 所属用户 / 经营状态 / 审核状态一律不动
        // 全部白名单字段用 LambdaUpdateWrapper 显式 set：尤其是 logo/description 等可清空字段，
        // MyBatis-Plus 的 updateById 默认忽略 null，会导致「清空头像/简介」无效、旧值残留。
        LambdaUpdateWrapper<MerchantProfile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MerchantProfile::getId, profile.getId())
                .set(MerchantProfile::getStoreName, dto.getStoreName().trim())
                .set(MerchantProfile::getDescription, blankToNull(dto.getDescription()))
                .set(MerchantProfile::getContactName, blankToNull(dto.getContactName()))
                .set(MerchantProfile::getContactPhone, contactPhone)
                .set(MerchantProfile::getAddress, blankToNull(dto.getAddress()))
                .set(MerchantProfile::getLogo, blankToNull(dto.getLogo()));
        profileMapper.update(null, wrapper);

        // 手机号双向联动：商家资料与个人中心维护的是同一个联系电话，
        // DTO 已限定为手机号，这里同步写入 user.phone；清空也同步。
        if (!phoneLinkService.syncStorePhoneToUser(userId, contactPhone)) {
            log.warn("商家 {} 提交了非手机号格式的联系电话，已拒绝同步", userId);
        }
        return toVO(profileMapper.selectById(profile.getId()));
    }

    /**
     * 上传店铺头像：校验逻辑复用用户头像的 {@link UploadedImageValidator}，
     * 身份只取 SecurityContext，不接受 merchantId / userId，因此不能改其他店铺。
     */
    @Override
    public MerchantLogoUploadVO uploadLogo(MultipartFile file) {
        Long userId = currentMerchantId();
        MerchantProfile profile = requireMyProfile();

        String extension = imageValidator.validate(file);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path avatarDir = uploadProperties.avatarDirPath().normalize();
        Path target = avatarDir.resolve(filename).normalize();
        if (!target.startsWith(avatarDir)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "店铺头像保存路径不合法");
        }

        try {
            Files.createDirectories(avatarDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("店铺头像文件保存失败, userId={}", userId, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "店铺头像保存失败，请稍后重试");
        }

        String logoUrl = "/uploads/avatars/" + filename;
        try {
            LambdaUpdateWrapper<MerchantProfile> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(MerchantProfile::getId, profile.getId()).set(MerchantProfile::getLogo, logoUrl);
            profileMapper.update(null, wrapper);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                log.warn("清理失败店铺头像文件失败: {}", target);
            }
            throw e;
        }

        log.info("商家 {} 更新店铺头像: {}", userId, logoUrl);
        return new MerchantLogoUploadVO(logoUrl);
    }

    /** 当前商家资料：不存在则按当前用户创建默认资料（懒初始化，兼容迁移后新建账号） */
    private MerchantProfile requireMyProfile() {
        Long userId = currentMerchantId();
        MerchantProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<MerchantProfile>()
                .eq(MerchantProfile::getUserId, userId));
        if (profile != null) {
            return profile;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前账号不存在");
        }
        profile = new MerchantProfile();
        profile.setUserId(userId);
        profile.setStoreName((user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername() : user.getNickname()) + " 的店铺");
        profile.setDescription("该店铺暂未填写简介。");
        profile.setStatus(MerchantProfile.STATUS_NORMAL);
        profile.setAuditStatus(AuditStatus.APPROVED.getValue());
        try {
            profileMapper.insert(profile);
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // 并发首次访问：唯一键冲突说明另一请求已创建，回读即可
            profile = profileMapper.selectOne(new LambdaQueryWrapper<MerchantProfile>()
                    .eq(MerchantProfile::getUserId, userId));
        }
        return profile;
    }

    /** 角色校验 + 当前商家 ID */
    private Long currentMerchantId() {
        SecurityUtils.requireRole(RoleCode.MERCHANT);
        return SecurityUtils.requireCurrentUser().getUserId();
    }

    private MerchantProfileVO toVO(MerchantProfile p) {
        MerchantProfileVO vo = new MerchantProfileVO();
        vo.setId(p.getId());
        vo.setUserId(p.getUserId());
        vo.setStoreName(p.getStoreName());
        vo.setLogo(p.getLogo());
        vo.setDescription(p.getDescription());
        vo.setContactName(p.getContactName());
        vo.setContactPhone(p.getContactPhone());
        vo.setAddress(p.getAddress());
        vo.setStatus(p.getStatus());
        vo.setStatusDescription(MerchantProfile.STATUS_NORMAL.equals(p.getStatus()) ? "正常经营" : "已暂停");
        vo.setAuditStatus(p.getAuditStatus());
        vo.setAuditStatusDescription(AuditStatus.PENDING.getValue().equals(p.getAuditStatus()) ? "待审核"
                : AuditStatus.REJECTED.getValue().equals(p.getAuditStatus()) ? "审核驳回" : "审核通过");
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
