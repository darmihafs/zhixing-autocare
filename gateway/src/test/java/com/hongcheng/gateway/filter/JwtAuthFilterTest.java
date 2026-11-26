package com.hongcheng.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.config.JwtProperties;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtAuthFilter 角色透传测试：
 * 验签后注入 X-User-Role，先剥离客户端伪造头；旧 Token 无 role 声明兜底 USER。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final String SECRET = "hongcheng-car-platform-jwt-secret-2026-dev-only";

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        filter = new JwtAuthFilter(properties, new ObjectMapper());
    }

    private ServerWebExchange captureDownstream() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("管理员 Token - 剥离伪造 X-User-Role 后注入 ADMIN")
    void filter_withAdminToken_stripsForgedRoleAndInjectsAdmin() {
        // given：客户端伪造 USER 角色与他人的 X-User-Id
        String token = JwtUtil.createToken(SECRET, 60_000L, 4L, "admin", "ADMIN");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/order/manage/page")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + token)
                .header(CommonConstants.HEADER_USER_ROLE, "USER")
                .header(CommonConstants.HEADER_USER_ID, "999")
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        ServerWebExchange downstream = captureDownstream();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE))
                .isEqualTo("ADMIN");
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID))
                .isEqualTo("4");
    }

    @Test
    @DisplayName("普通用户 Token - 透传 USER 角色")
    void filter_withUserToken_injectsUserRole() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan", "USER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/order/page")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + token)
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        assertThat(captureDownstream().getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("旧 Token 无 role 声明 - 兜底透传 USER")
    void filter_withLegacyToken_defaultsRoleToUser() {
        // given：手工签发不含 role 声明的历史 Token
        String legacyToken = Jwts.builder()
                .subject("1")
                .claim(CommonConstants.CLAIM_USERNAME, "zhangsan")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/order/page")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + legacyToken)
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        assertThat(captureDownstream().getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("缺少凭证 - 401 且不进入下游")
    void filter_withoutToken_returns401() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/order/manage/page").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // when
        filter.filter(exchange, chain).block();

        // then
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("可选鉴权匿名请求 - 剥离伪造身份头且不 401")
    void optionalAuth_anonymous_stripsForgedHeaders() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/ai/chat")
                .header(CommonConstants.HEADER_USER_ID, "999")
                .header(CommonConstants.HEADER_USER_ROLE, "ADMIN")
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        ServerWebExchange downstream = captureDownstream();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE)).isNull();
    }

    @Test
    @DisplayName("可选鉴权携带管理员 Token - 正常透传 ADMIN")
    void optionalAuth_withAdminToken_injectsAdminRole() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 4L, "admin", "ADMIN");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/ai/chat")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + token)
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        assertThat(captureDownstream().getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE))
                .isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("模拟 Token - 剥离伪造 X-Impersonator-Id 后按 claim 注入，角色为目标 USER")
    void filter_withImpersonationToken_stripsForgedAndInjectsImpersonator() {
        // given：客户端伪造模拟发起者头
        String token = JwtUtil.createImpersonationToken(SECRET, 900_000L, 1L, "zhangsan", 4L, "admin");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/user/impersonation/exit")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + token)
                .header(CommonConstants.HEADER_IMPERSONATOR_ID, "999")
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        ServerWebExchange downstream = captureDownstream();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_IMPERSONATOR_ID))
                .isEqualTo("4");
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID))
                .isEqualTo("1");
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("普通 Token 伪造 X-Impersonator-Id - 剥离后下游无该头")
    void filter_withNormalToken_stripsForgedImpersonatorHeader() {
        // given
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan", "USER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/user/impersonation/exit")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + token)
                .header(CommonConstants.HEADER_IMPERSONATOR_ID, "4")
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        assertThat(captureDownstream().getRequest().getHeaders().getFirst(CommonConstants.HEADER_IMPERSONATOR_ID))
                .isNull();
    }

    @Test
    @DisplayName("可选鉴权携带模拟 Token - 透传目标身份与模拟发起者头")
    void optionalAuth_withImpersonationToken_injectsImpersonator() {
        // given
        String token = JwtUtil.createImpersonationToken(SECRET, 900_000L, 1L, "zhangsan", 4L, "admin");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/ai/chat")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + token)
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        // when
        filter.filter(MockServerWebExchange.from(request), chain).block();

        // then
        ServerWebExchange downstream = captureDownstream();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID))
                .isEqualTo("1");
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_IMPERSONATOR_ID))
                .isEqualTo("4");
    }

    @Test
    @DisplayName("公开白名单路径 - 剥离全部伪造身份与内部/管理请求头")
    void publicPrefix_stripsAllForgedTrustHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/review/item/1/100")
                .header(CommonConstants.HEADER_USER_ID, "999")
                .header(CommonConstants.HEADER_USERNAME, "attacker")
                .header(CommonConstants.HEADER_USER_ROLE, "ADMIN")
                .header(CommonConstants.HEADER_IMPERSONATOR_ID, "4")
                .header(CommonConstants.HEADER_ADMIN_TOKEN, "hc-admin-2026-dev-token")
                .header(CommonConstants.HEADER_INTERNAL_TOKEN, "hc-internal-2026-dev-token")
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(MockServerWebExchange.from(request), chain).block();

        ServerWebExchange downstream = captureDownstream();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USERNAME)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_IMPERSONATOR_ID)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_ADMIN_TOKEN)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_INTERNAL_TOKEN)).isNull();
    }

    @Test
    @DisplayName("未规范化路径穿越 - 含 .. 或 %2e 直接 403 拦截")
    void pathTraversal_returns403AndBlocksDownstream() {
        MockServerHttpRequest dotDotReq = MockServerHttpRequest.get("/api/review/item/../../review/manage/page").build();
        MockServerWebExchange dotDotExchange = MockServerWebExchange.from(dotDotReq);
        filter.filter(dotDotExchange, chain).block();
        assertThat(dotDotExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        MockServerHttpRequest encodedReq = MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.GET,
                        java.net.URI.create("/api/shop/%2e%2e/manage/shop/page"))
                .build();
        MockServerWebExchange encodedExchange = MockServerWebExchange.from(encodedReq);
        filter.filter(encodedExchange, chain).block();
        assertThat(encodedExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        MockServerHttpRequest doubleEncodedReq = MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.GET,
                        java.net.URI.create("/api/shop/%252e%252e/manage/shop/page"))
                .build();
        MockServerWebExchange doubleEncodedExchange = MockServerWebExchange.from(doubleEncodedReq);
        filter.filter(doubleEncodedExchange, chain).block();
        assertThat(doubleEncodedExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("普通已登录请求 - 剥离客户端传入的 X-Admin-Token 与 X-Internal-Token")
    void authenticatedRequest_stripsAdminAndInternalTokens() {
        String token = JwtUtil.createToken(SECRET, 60_000L, 1L, "zhangsan", "USER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/manage/shop/page")
                .header(CommonConstants.HEADER_AUTHORIZATION, CommonConstants.TOKEN_PREFIX + token)
                .header(CommonConstants.HEADER_ADMIN_TOKEN, "hc-admin-2026-dev-token")
                .header(CommonConstants.HEADER_INTERNAL_TOKEN, "hc-internal-2026-dev-token")
                .build();
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(MockServerWebExchange.from(request), chain).block();

        ServerWebExchange downstream = captureDownstream();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_ADMIN_TOKEN)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_INTERNAL_TOKEN)).isNull();
        assertThat(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ROLE)).isEqualTo("USER");
    }
}