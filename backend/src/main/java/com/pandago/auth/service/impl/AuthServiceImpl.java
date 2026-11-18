package com.pandago.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.auth.dto.LoginDTO;
import com.pandago.auth.dto.RegisterDTO;
import com.pandago.auth.dto.UserProfileUpdateDTO;
import com.pandago.auth.service.AuthService;
import com.pandago.auth.vo.LoginVO;
import com.pandago.auth.vo.UserInfoVO;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.CommonStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.role.entity.Role;
import com.pandago.role.mapper.RoleMapper;
import com.pandago.security.LoginUserPrincipal;
import com.pandago.security.JwtTokenProvider;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.entity.UserRole;
import com.pandago.user.mapper.UserMapper;
import com.pandago.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 认证业务实现。
 *
 * <p>事务边界：register 用 @Transactional 保证「创建用户 + 分配 USER 角色」原子完成；
 * 若任一环节失败（如唯一键冲突），整体回滚，不产生"有用户无角色"的脏数据。</p>
 *
 * <p>并发防重：username 上有唯一索引 uk_user_username。Service 先查一次给出友好提示，
 * 但最终一致性由数据库唯一索引保证——两个并发请求同时通过检查时，必有一个 insert
 * 抛 DuplicateKeyException，这里捕获后转成"用户名已存在"，不产生重复用户。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        // 1. 二次校验确认密码（DTO 层无法交叉校验两个字段）
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }

        String username = dto.getUsername().trim();
        // 2. 先查一次，给出可读提示（非并发安全的最终手段）
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }

        // 3. 查 USER 角色。找不到说明初始化数据未执行，属于环境错误
        Role userRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, RoleCode.USER.getCode())
                .last("LIMIT 1"));
        if (userRole == null) {
            log.error("角色初始化数据缺失：未找到 USER 角色，请先执行 database/init-data.sql");
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "系统角色配置异常，请联系管理员");
        }

        // 4. 组装用户：密码用 BCrypt 哈希，状态默认启用；角色不接受客户端输入
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() != null && !dto.getNickname().isBlank()
                ? dto.getNickname().trim() : username);
        user.setPhone(dto.getPhone());
        user.setStatus(CommonStatus.ENABLED.getValue());

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 5. 并发兜底：唯一索引冲突
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }

        // 6. 同一事务内分配 USER 角色
        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(userRole.getId());
        userRoleMapper.insert(relation);
        log.info("用户注册成功: id={}, username={}", user.getId(), username);
        return user.getId();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 按用户名查询（唯一索引保证最多一条）
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername().trim())
                .last("LIMIT 1"));
        if (user == null) {
            // 故意不区分"用户不存在"和"密码错误"，避免账号枚举
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 2. 禁用用户不允许登录
        if (user.getStatus() == null || user.getStatus() != CommonStatus.ENABLED.getValue()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        // 3. BCrypt 校验：matches 会自动从哈希中解析盐值并重新计算
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 4. 查询角色（用于 JWT claims 与前端展示；权限的唯一来源是数据库，不是前端选择的身份）
        List<RoleCode> roleCodes = findRoleCodes(user.getId());

        // 4.1 身份匹配校验：身份选择页传来的 loginAs 只是"用户想以什么身份进入"的意图，
        //     必须与该账号在数据库中真实拥有的角色一致才放行。这样即使前端被篡改，
        //     普通用户也无法靠"选择商家登录"进入后台。老接口不传 loginAs 时跳过此校验。
        if (dto.getLoginAs() != null && !dto.getLoginAs().isBlank()) {
            RoleCode wanted = RoleCode.fromCode(dto.getLoginAs());
            if (wanted == null || !roleCodes.contains(wanted)) {
                log.warn("身份不匹配，拒绝登录: userId={}, loginAs={}, actualRoles={}",
                        user.getId(), dto.getLoginAs(), roleCodes);
                throw new BusinessException(ResultCode.FORBIDDEN,
                        "该账号没有「" + (wanted == null ? dto.getLoginAs() : wanted.getDescription())
                                + "」身份，无法以此身份登录");
            }
        }

        // 5. 签发 JWT：只放 id / username / roles，不含密码
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roleCodes);

        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());
        return LoginVO.builder()
                .token(token)
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .roles(roleCodes.stream().map(RoleCode::getCode).toList())
                .build();
    }

    @Override
    public UserInfoVO currentUser() {
        LoginUserPrincipal principal = SecurityUtils.requireCurrentUser();
        User user = userMapper.selectById(principal.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已注销");
        }
        return toUserInfoVO(user);
    }

    @Override
    public UserInfoVO updateMyProfile(UserProfileUpdateDTO dto) {
        // 身份只从 SecurityContext 读取，绝不相信请求体里的 userId；因此只能改自己
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已注销");
        }
        // 白名单：只改昵称与手机号；username / passwordHash / status / 角色 / id 一律不动
        // 用 UpdateWrapper 显式 set，保证 phone 传 null 时也能清空（updateById 默认忽略 null 字段）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> updateWrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId)
                .set(User::getNickname, dto.getNickname().trim())
                .set(User::getPhone, blankToNull(dto.getPhone()));
        userMapper.update(null, updateWrapper);
        log.info("用户 {} 更新个人资料", userId);
        return toUserInfoVO(userMapper.selectById(userId));
    }

    /** 实体 → VO：只挑选安全字段，VO 中没有 passwordHash 等敏感字段 */
    private UserInfoVO toUserInfoVO(User user) {
        return UserInfoVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .phone(user.getPhone())
                .roles(findRoleCodes(user.getId()).stream().map(RoleCode::getCode).toList())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 按用户 id 查询角色编码，转换为受控枚举；未知角色忽略 */
    private List<RoleCode> findRoleCodes(Long userId) {
        List<Role> roles = roleMapper.selectRolesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            // 理论上不会发生（注册时强制分配），降级为 USER 保证前端基本可用
            log.warn("用户 {} 无角色记录，降级为 USER", userId);
            return List.of(RoleCode.USER);
        }
        return roles.stream()
                .map(Role::getCode)
                .map(RoleCode::fromCode)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
