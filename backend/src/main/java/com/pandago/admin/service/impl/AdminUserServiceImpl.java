package com.pandago.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pandago.admin.service.AdminUserService;
import com.pandago.admin.service.OperationLogService;
import com.pandago.admin.vo.AdminUserVO;
import com.pandago.common.api.PageResult;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.CommonStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.role.entity.Role;
import com.pandago.role.mapper.RoleMapper;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.entity.UserRole;
import com.pandago.user.mapper.UserMapper;
import com.pandago.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理端用户管理实现：ADMIN 角色由 Controller/URL 与服务层双重校验。 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final OperationLogService operationLogService;

    @Override
    public PageResult<AdminUserVO> page(String username, long pageNum, long pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(username), User::getUsername, username)
                .orderByAsc(User::getId);
        IPage<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<User> users = page.getRecords();

        // 角色批量填充，避免逐用户 N+1
        Map<Long, List<String>> rolesByUser = new HashMap<>();
        Map<Long, String> roleCodeById = new HashMap<>();
        for (Role role : roleMapper.selectList(null)) {
            roleCodeById.put(role.getId(), role.getCode());
        }
        if (!users.isEmpty()) {
            List<Long> userIds = users.stream().map(User::getId).toList();
            for (UserRole ur : userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().in(UserRole::getUserId, userIds))) {
                String code = roleCodeById.get(ur.getRoleId());
                if (code != null) {
                    rolesByUser.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(code);
                }
            }
        }

        List<AdminUserVO> vos = users.stream().map(user -> {
            AdminUserVO vo = new AdminUserVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setPhone(user.getPhone());
            vo.setStatus(user.getStatus());
            vo.setRoles(rolesByUser.getOrDefault(user.getId(), List.of()));
            vo.setCreatedAt(user.getCreatedAt());
            return vo;
        }).toList();
        return PageResult.of(vos, page.getTotal(), pageNum, pageSize, page.getPages());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        SecurityUtils.requireRole(RoleCode.ADMIN);
        if (status == null || (status != CommonStatus.ENABLED.getValue()
                && status != CommonStatus.DISABLED.getValue())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态只能为 1 启用或 0 禁用");
        }
        Long currentAdminId = SecurityUtils.requireCurrentUser().getUserId();
        if (userId.equals(currentAdminId)) {
            throw new BusinessException(ResultCode.CONFLICT, "不能禁用当前登录的账号");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        operationLogService.record("USER",
                status == CommonStatus.ENABLED.getValue() ? "ENABLE" : "DISABLE",
                "USER", userId, "管理员变更用户状态为 " + status);
    }
}
