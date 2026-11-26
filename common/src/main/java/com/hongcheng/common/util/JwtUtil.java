package com.hongcheng.common.util;

import com.hongcheng.common.constant.CommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（jjwt 0.12.x API）。
 * <p>
 * 无状态静态方法，secret 由调用方从配置注入（hc.jwt.secret），
 * 便于 gateway 与 user-service 共用同一密钥而不在 common 里绑定 Spring 环境。
 * HS256 要求 secret 至少 32 字节，配置值不满足时签发/解析会抛 WeakKeyException。
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    /**
     * 签发 Token。
     *
     * @param secret   签名密钥（>=32 字节）
     * @param expireMs 有效期毫秒
     * @param userId   用户ID，写入 subject
     * @param username 用户名，写入自定义 claim
     * @return 紧凑序列化 Token
     */
    public static String createToken(String secret, long expireMs, Long userId, String username) {
        return createToken(secret, expireMs, userId, username, CommonConstants.ROLE_USER);
    }

    /**
     * 签发 Token（带角色声明）。
     *
     * @param role 用户角色（USER/ADMIN），写入自定义 claim，供网关透传 X-User-Role
     */
    public static String createToken(String secret, long expireMs, Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CommonConstants.CLAIM_USERNAME, username)
                .claim(CommonConstants.CLAIM_ROLE, role == null ? CommonConstants.ROLE_USER : role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(buildKey(secret))
                .compact();
    }

    /**
     * 签发管理员模拟用户视角的临时 Token（User Impersonation）。
     * <p>
     * subject/username/role 均为目标普通用户（role 固定 USER，天然被管理端拦截器拒绝）；
     * 额外携带 impersonatorId/impersonatedBy 声明供恢复身份与审计。
     * 短有效期由调用方传入，不伴随任何 Refresh Token。
     *
     * @param targetUserId   被模拟的普通用户ID
     * @param targetUsername 被模拟的普通用户名
     * @param impersonatorId 发起模拟的管理员ID
     * @param impersonatedBy 发起模拟的管理员用户名
     */
    public static String createImpersonationToken(String secret, long expireMs, Long targetUserId,
                                                  String targetUsername, Long impersonatorId,
                                                  String impersonatedBy) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(targetUserId))
                .claim(CommonConstants.CLAIM_USERNAME, targetUsername)
                .claim(CommonConstants.CLAIM_ROLE, CommonConstants.ROLE_USER)
                .claim(CommonConstants.CLAIM_IMPERSONATOR_ID, String.valueOf(impersonatorId))
                .claim(CommonConstants.CLAIM_IMPERSONATED_BY, impersonatedBy)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(buildKey(secret))
                .compact();
    }

    /**
     * 解析并验签 Token。
     *
     * @throws JwtException 签名不符、过期、格式非法时抛出
     */
    public static Claims parseToken(String secret, String token) {
        return Jwts.parser()
                .verifyWith(buildKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Token 中取用户ID；Token 非法时返回 null */
    public static Long getUserId(String secret, String token) {
        try {
            return Long.valueOf(parseToken(secret, token).getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 校验 Token 是否有效（验签 + 有效期） */
    public static boolean isValid(String secret, String token) {
        try {
            parseToken(secret, token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private static SecretKey buildKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}