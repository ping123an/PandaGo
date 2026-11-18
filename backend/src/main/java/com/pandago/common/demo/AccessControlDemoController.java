package com.pandago.common.demo;

import com.pandago.common.api.Result;
import com.pandago.security.LoginUserPrincipal;
import com.pandago.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 角色权限演示接口（阶段 3 教学用）。
 *
 * <p>用途：用最小代码展示三种角色的接口如何声明权限，便于用真实 Token 验证
 * "USER 访问 ADMIN 接口被拒绝"。这些接口使用 {@link PreAuthorize} 方法级鉴权，
 * 与 SecurityConfig 中 /admin/**、/merchant/** 的 URL 级规则共同构成两道防线。</p>
 *
 * <p>注意：这里只演示"角色"这一层。真正的业务接口（订单、地址、商家商品）
 * 还必须做"资源归属"校验，见 AccessControlDemoController 末尾说明与后续阶段实现。</p>
 */
@RestController
@RequestMapping("/api/v1/demo")
public class AccessControlDemoController {

    /** 任意已登录用户可访问（SecurityConfig 的 anyRequest().authenticated() 已保证） */
    @GetMapping("/user")
    public Result<Map<String, Object>> userOnly() {
        LoginUserPrincipal user = SecurityUtils.requireCurrentUser();
        return Result.ok(Map.of(
                "message", "普通用户接口：任何已登录用户均可访问",
                "currentUserId", user.getUserId(),
                "username", user.getUsername()));
    }

    /** 仅商家（ADMIN 也放行，便于平台代管） */
    @GetMapping("/merchant")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public Result<Map<String, Object>> merchantOnly() {
        LoginUserPrincipal user = SecurityUtils.requireCurrentUser();
        return Result.ok(Map.of(
                "message", "商家接口：仅 MERCHANT / ADMIN 角色可访问",
                "username", user.getUsername()));
    }

    /** 仅管理员 */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> adminOnly() {
        LoginUserPrincipal user = SecurityUtils.requireCurrentUser();
        return Result.ok(Map.of(
                "message", "管理员接口：仅 ADMIN 角色可访问",
                "username", user.getUsername()));
    }

    /*
     * ============ 角色校验 ≠ 资源归属校验（教学重点） ============
     *
     * hasRole('ADMIN') 只回答"你是谁（什么角色）"，不回答"这条数据是不是你的"。
     * 例如商家修改商品：即使通过了 hasRole('MERCHANT')，仍必须校验
     *   product.merchant_id == currentUser.id
     * 否则商家 A 就能改商家 B 的商品（越权访问，IDOR 漏洞）。
     *
     * 通用实现思路（后续订单/商品阶段落地）：
     *   1. 从 SecurityUtils.requireCurrentUser() 取当前用户 id，绝不信任客户端传的 userId；
     *   2. 在查询/更新条件中强制拼接归属字段，如
     *      SELECT ... WHERE id = #{id} AND merchant_id = #{currentUserId}
     *   3. 更新/删除时检查影响行数，为 0 说明数据不存在或不归属自己，统一返回 404/403；
     *   4. 管理员是特例：可按角色跳过归属过滤，但要走独立、可审计的_admin 接口。
     *
     * 判断标准：只要 URL 路径里带了资源 id，就要问一句"这个 id 属于当前登录者吗？"
     */
}
