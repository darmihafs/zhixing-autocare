package com.hongcheng.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.config.JwtProperties;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.common.util.JwtUtil;
import com.hongcheng.user.config.ImpersonationProperties;
import com.hongcheng.user.dto.ImpersonationVO;
import com.hongcheng.user.dto.UserAdminVO;
import com.hongcheng.user.entity.User;
import com.hongcheng.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UserManageService 单元测试（mock Mapper，不连库）：
 * 管理端用户列表脱敏边界 + Impersonation 签发规则与审计字段。
 */
@ExtendWith(MockitoExtension.class)
class UserManageServiceTest {

    private static final String SECRET = "hongcheng-car-platform-jwt-secret-2026-dev-only";

    @Mock
    private UserMapper userMapper;

    private UserManageService service;

    @BeforeEach
    void setUp() {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret(SECRET);
        jwt.setExpireMs(7_200_000L);
        ImpersonationProperties impersonation = new ImpersonationProperties();
        impersonation.setExpireMs(900_000L);
        service = new UserManageService(userMapper, jwt, impersonation);
    }

    private User user(Long id, String username, String role, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username + "昵称");
        user.setPhone("13812345678");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    @Test
    @DisplayName("管理端分页 - 真实用户名/昵称且手机号打码、不含密码字段")
    void pageForAdmin_returnsRealUsernameAndMaskedPhone() {
        // given
        when(userMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<User> page = invocation.getArgument(0);
            page.setRecords(List.of(user(1L, "zhangsan", "USER", 1)));
            page.setTotal(1);
            return page;
        });

        // when
        Page<UserAdminVO> page = service.pageForAdmin("zhang", 1, 10);

        // then
        assertThat(page.getTotal()).isEqualTo(1);
        UserAdminVO vo = page.getRecords().get(0);
        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getUsername()).isEqualTo("zhangsan");
        assertThat(vo.getNickname()).isEqualTo("zhangsan昵称");
        assertThat(vo.getPhone()).isEqualTo("138****5678");
        assertThat(vo.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("发起模拟 - 短效 Token 携带目标身份与 impersonatorId/impersonatedBy")
    void impersonate_success_issuesShortLivedImpersonationToken() {
        // given
        when(userMapper.selectById(1L)).thenReturn(user(1L, "zhangsan", "USER", 1));

        // when
        ImpersonationVO vo = service.impersonate(4L, "admin", 1L);

        // then
        assertThat(vo.getExpiresIn()).isEqualTo(900L);
        assertThat(vo.getImpersonatorId()).isEqualTo(4L);
        assertThat(vo.getImpersonatedBy()).isEqualTo("admin");
        assertThat(vo.getUser().getUsername()).isEqualTo("zhangsan");
        assertThat(vo.getUser().getRole()).isEqualTo("USER");
        Claims claims = JwtUtil.parseToken(SECRET, vo.getToken());
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(CommonConstants.CLAIM_ROLE, String.class)).isEqualTo("USER");
        assertThat(claims.get(CommonConstants.CLAIM_IMPERSONATOR_ID, String.class)).isEqualTo("4");
        assertThat(claims.get(CommonConstants.CLAIM_IMPERSONATED_BY, String.class)).isEqualTo("admin");
    }

    @Test
    @DisplayName("模拟其他管理员 - 403 且不签发")
    void impersonate_whenTargetAdmin_throwsForbidden() {
        // given
        when(userMapper.selectById(5L)).thenReturn(user(5L, "otheradmin", "ADMIN", 1));

        // when / then
        assertThatThrownBy(() -> service.impersonate(4L, "admin", 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("模拟自身 - 400 且不查库")
    void impersonate_whenSelf_throwsBadRequest() {
        assertThatThrownBy(() -> service.impersonate(4L, "admin", 4L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("模拟禁用账号 - 1004")
    void impersonate_whenTargetDisabled_throwsUserDisabled() {
        // given
        when(userMapper.selectById(1L)).thenReturn(user(1L, "zhangsan", "USER", 0));

        // when / then
        assertThatThrownBy(() -> service.impersonate(4L, "admin", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.USER_DISABLED.getCode());
    }

    @Test
    @DisplayName("模拟不存在用户 - 404")
    void impersonate_whenTargetMissing_throwsNotFound() {
        // given
        when(userMapper.selectById(999L)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> service.impersonate(4L, "admin", 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("历史 role 为空的目标 - 视为普通用户可模拟")
    void impersonate_whenTargetRoleNull_treatedAsUser() {
        // given
        when(userMapper.selectById(1L)).thenReturn(user(1L, "zhangsan", null, 1));

        // when
        ImpersonationVO vo = service.impersonate(4L, "admin", 1L);

        // then
        assertThat(vo.getUser().getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("退出模拟 - 非模拟态（无 impersonatorId）抛 400")
    void exitImpersonation_whenNotImpersonating_throwsBadRequest() {
        assertThatThrownBy(() -> service.exitImpersonation(null, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("退出模拟 - 模拟态正常审计通过不抛异常")
    void exitImpersonation_whenImpersonating_passes() {
        service.exitImpersonation(4L, 1L);
    }
}