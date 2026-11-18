package com.pandago.security;

import com.pandago.common.enums.RoleCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与校验工具。
 *
 * <p>职责单一：只把「用户身份 + 角色」封装成签名令牌，或从令牌还原身份。
 * 不查数据库、不感知 HTTP、不写 SecurityContext——这些分别属于 Service、Filter 和
 * Spring Security。令牌载荷（claims）只包含 id / username / roles，
 * <strong>绝不包含密码、密码哈希或任何敏感信息</strong>。</p>
 *
 * <p>算法使用 HMAC-SHA256（HS256），密钥必须至少 32 字节，来自环境变量
 * PANDAGO_JWT_SECRET。校验时同时验证签名与过期时间，任一失败都视为无效令牌。</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 令牌中存放用户 id 的 claim 名 */
    public static final String CLAIM_USER_ID = "uid";
    /** 令牌中存放用户名的 claim 名 */
    public static final String CLAIM_USERNAME = "username";
    /** 令牌中存放角色编码列表的 claim 名，如 ["USER"] */
    public static final String CLAIM_ROLES = "roles";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(@Value("${pandago.jwt.secret}") String secret,
                            @Value("${pandago.jwt.expiration-ms}") long expirationMs) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT 密钥长度不足：HS256 要求至少 32 字节，请设置 PANDAGO_JWT_SECRET");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * 签发访问令牌。
     *
     * @param userId   用户 id，作为 JWT 的 sub（Subject）
     * @param username 登录账号，仅用于展示与日志关联，不含敏感信息
     * @param roles    角色编码集合，如 [USER]
     * @return 紧凑序列化后的 JWT 字符串
     */
    public String generateToken(Long userId, String username, List<RoleCode> roles) {
        return generateToken(userId, username, roles, expirationMs);
    }

    /**
     * 可指定有效期的签发方法，主要用于测试过期场景（如传入负毫秒数）。
     * 生产代码请调用 {@link #generateToken(Long, String, List)}。
     */
    public String generateToken(Long userId, String username, List<RoleCode> roles, long validMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validMs);
        List<String> roleCodes = roles.stream().map(RoleCode::getCode).toList();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roleCodes)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解析并校验令牌。验签失败、格式错误、过期都会抛出对应子类异常。
     * 调用方（Filter）统一捕获后按 401 处理。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从令牌取用户 id；调用前应确保令牌已通过 validate */
    public Long getUserId(String token) {
        return parseClaims(token).get(CLAIM_USER_ID, Long.class);
    }

    public String getUsername(String token) {
        return parseClaims(token).get(CLAIM_USERNAME, String.class);
    }

    /** 从令牌取角色编码（如 USER / ADMIN），转换为受控枚举 */
    @SuppressWarnings("unchecked")
    public List<RoleCode> getRoles(String token) {
        List<String> codes = parseClaims(token).get(CLAIM_ROLES, List.class);
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .map(RoleCode::fromCode)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 校验令牌签名与有效期，失败返回 false（不抛异常到外层） */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("JWT 已过期");
        } catch (SignatureException ex) {
            log.debug("JWT 签名不匹配");
        } catch (MalformedJwtException ex) {
            log.debug("JWT 格式错误");
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT 校验失败: {}", ex.getMessage());
        }
        return false;
    }

    /** 令牌有效期（毫秒），供登录响应返回给前端用于提示续期 */
    public long getExpirationMs() {
        return expirationMs;
    }
}
