package com.pandago.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.merchant.dto.MerchantProfileUpdateDTO;
import com.pandago.merchant.entity.MerchantProfile;
import com.pandago.merchant.mapper.MerchantProfileMapper;
import com.pandago.merchant.service.MerchantProfileService;
import com.pandago.merchant.vo.MerchantProfileVO;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商家店铺资料业务实现（V9）。
 *
 * <p>安全要点：
 * 1. 所有方法先校验 MERCHANT 角色并从 SecurityContext 读 userId，不接受前端 merchantId；
 * 2. 更新只用 DTO 白名单字段 set，id / userId / status / auditStatus 永远不会写库；
 * 3. 若该商家尚无资料（迁移后新建的商家账号），首次查询自动创建一份默认资料。</p>
 */
@Service
@RequiredArgsConstructor
public class MerchantProfileServiceImpl implements MerchantProfileService {

    private final MerchantProfileMapper profileMapper;
    private final UserMapper userMapper;

    @Override
    public MerchantProfileVO getMyProfile() {
        MerchantProfile profile = requireMyProfile();
        return toVO(profile);
    }

    @Override
    public MerchantProfileVO updateMyProfile(MerchantProfileUpdateDTO dto) {
        MerchantProfile profile = requireMyProfile();

        // 只更新白名单字段；店铺 ID / 所属用户 / 经营状态 / 审核状态一律不动
        // 全部白名单字段用 LambdaUpdateWrapper 显式 set：尤其是 logo/description 等可清空字段，
        // MyBatis-Plus 的 updateById 默认忽略 null，会导致「清空头像/简介」无效、旧值残留。
        LambdaUpdateWrapper<MerchantProfile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MerchantProfile::getId, profile.getId())
                .set(MerchantProfile::getStoreName, dto.getStoreName().trim())
                .set(MerchantProfile::getDescription, blankToNull(dto.getDescription()))
                .set(MerchantProfile::getContactName, blankToNull(dto.getContactName()))
                .set(MerchantProfile::getContactPhone, blankToNull(dto.getContactPhone()))
                .set(MerchantProfile::getAddress, blankToNull(dto.getAddress()))
                .set(MerchantProfile::getLogo, blankToNull(dto.getLogo()));
        profileMapper.update(null, wrapper);
        return toVO(profileMapper.selectById(profile.getId()));
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
