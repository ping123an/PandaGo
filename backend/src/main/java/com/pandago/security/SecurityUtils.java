package com.pandago.security;

import com.pandago.common.enums.RoleCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.common.api.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户访问工具（供 Service / Controller 使用）。
 *
 * <p>安全原则：用户身份必须从 SecurityContext 读取，绝不接受客户端传来的 userId 作为
 * "当前用户"。这是后续实现资源归属校验的基础——例如订单查询必须用这里的 id 作为
 * 查询条件的一部分，而不是信任 query 参数里的 userId。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 获取当前登录主体；未登录抛出 401 业务异常 */
    public static LoginUserPrincipal requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUserPrincipal principal)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return principal;
    }

    /** 尝试获取当前用户 id；未登录返回 null（用于公开接口的可选身份） */
    public static Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    /**
     * 校验当前登录用户拥有指定角色。
     *
     * <p>这是 Service 层的第二道防线：即使前端隐藏按钮被绕过、或 URL 规则配置有误，
     * 业务方法仍会拒绝无角色用户。角色信息来自 JWT 验签后写入的 GrantedAuthority，
     * 不接受请求体中的 role 字段。</p>
     */
    public static void requireRole(RoleCode role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        boolean hasRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role.authority()::equals);
        if (!hasRole) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前账号没有该操作权限");
        }
    }
}
