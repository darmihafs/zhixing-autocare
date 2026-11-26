package com.hongcheng.common.util;

import com.hongcheng.common.constant.CommonConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 单元测试：签发/解析/过期/篡改/错密钥。
 */
class JwtUtilTest {

    private static final String SECRET = "hongcheng-car-platform-jwt-secret-2026-dev-only";
    private static final String OTHER_SECRET = "another-secret-key-for-negative-test-0123456789";

    @Test
    @DisplayName("签发后解析 - subject 与 username claim 原样返回")
    void createToken_thenParse_returnsClaims() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan");

        // when
        Claims claims = JwtUtil.parseToken(SECRET, token);

        // then
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("username", String.class)).isEqualTo("zhangsan");
        assertThat(JwtUtil.getUserId(SECRET, token)).isEqualTo(1L);
        assertThat(JwtUtil.isValid(SECRET, token)).isTrue();
    }

    @Test
    @DisplayName("过期 Token - 解析抛 ExpiredJwtException 且 isValid 为 false")
    void createToken_whenExpired_isInvalid() {
        // given：有效期为负，签发即过期
        String token = JwtUtil.createToken(SECRET, -1_000L, 1L, "zhangsan");

        // when / then
        assertThatThrownBy(() -> JwtUtil.parseToken(SECRET, token))
                .isInstanceOf(ExpiredJwtException.class);
        assertThat(JwtUtil.isValid(SECRET, token)).isFalse();
        assertThat(JwtUtil.getUserId(SECRET, token)).isNull();
    }

    @Test
    @DisplayName("错误密钥 - 验签失败抛 JwtException")
    void parseToken_withWrongSecret_throwsJwtException() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan");

        // when / then
        assertThatThrownBy(() -> JwtUtil.parseToken(OTHER_SECRET, token))
                .isInstanceOf(JwtException.class);
        assertThat(JwtUtil.isValid(OTHER_SECRET, token)).isFalse();
    }

    @Test
    @DisplayName("篡改 Token - isValid 为 false 且不抛异常")
    void isValid_whenTampered_returnsFalse() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan");

        // when / then
        assertThat(JwtUtil.isValid(SECRET, token + "tampered")).isFalse();
        assertThat(JwtUtil.isValid(SECRET, "not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("带角色签发 - role 声明原样返回（ADMIN）")
    void createToken_withAdminRole_carriesRoleClaim() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 4L, "admin", "ADMIN");

        // when
        Claims claims = JwtUtil.parseToken(SECRET, token);

        // then
        assertThat(claims.get(CommonConstants.CLAIM_ROLE, String.class)).isEqualTo("ADMIN");
        assertThat(claims.getSubject()).isEqualTo("4");
    }

    @Test
    @DisplayName("旧版四参签发 - 默认补 USER 角色声明")
    void createToken_withoutRole_defaultsToUser() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan");

        // when
        Claims claims = JwtUtil.parseToken(SECRET, token);

        // then
        assertThat(claims.get(CommonConstants.CLAIM_ROLE, String.class)).isEqualTo("USER");
    }

    @Test
    @DisplayName("角色传 null - 兜底为 USER 而非空声明")
    void createToken_withNullRole_defaultsToUser() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan", null);

        // when
        Claims claims = JwtUtil.parseToken(SECRET, token);

        // then
        assertThat(claims.get(CommonConstants.CLAIM_ROLE, String.class)).isEqualTo("USER");
    }

    @Test
    @DisplayName("模拟 Token - subject/role 为目标用户且携带 impersonatorId/impersonatedBy")
    void createImpersonationToken_carriesTargetAndImpersonatorClaims() {
        // given
        String token = JwtUtil.createImpersonationToken(SECRET, 900_000L, 1L, "zhangsan", 4L, "admin");

        // when
        Claims claims = JwtUtil.parseToken(SECRET, token);

        // then
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(CommonConstants.CLAIM_USERNAME, String.class)).isEqualTo("zhangsan");
        assertThat(claims.get(CommonConstants.CLAIM_ROLE, String.class)).isEqualTo("USER");
        assertThat(claims.get(CommonConstants.CLAIM_IMPERSONATOR_ID, String.class)).isEqualTo("4");
        assertThat(claims.get(CommonConstants.CLAIM_IMPERSONATED_BY, String.class)).isEqualTo("admin");
        assertThat(JwtUtil.getUserId(SECRET, token)).isEqualTo(1L);
    }

    @Test
    @DisplayName("模拟 Token 短有效期到期 - 验签失败且普通登录 Token 不受影响")
    void createImpersonationToken_whenExpired_isInvalid() {
        // given：有效期为负，签发即过期
        String expired = JwtUtil.createImpersonationToken(SECRET, -1_000L, 1L, "zhangsan", 4L, "admin");
        String normal = JwtUtil.createToken(SECRET, 60_000L, 4L, "admin", "ADMIN");

        // when / then
        assertThat(JwtUtil.isValid(SECRET, expired)).isFalse();
        assertThat(JwtUtil.isValid(SECRET, normal)).isTrue();
    }
}