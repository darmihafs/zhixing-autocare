package com.hongcheng.user.service;

import com.hongcheng.common.config.JwtProperties;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.common.util.JwtUtil;
import com.hongcheng.user.dto.LoginRequest;
import com.hongcheng.user.dto.LoginVO;
import com.hongcheng.user.dto.RegisterRequest;
import com.hongcheng.user.dto.UserBriefVO;
import com.hongcheng.user.dto.UserVO;
import com.hongcheng.user.entity.User;
import com.hongcheng.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试（mock Mapper，不连库）。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String SECRET = "hongcheng-car-platform-jwt-secret-2026-dev-only";

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpireMs(7_200_000L);
        userService = new UserService(userMapper, properties);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("123456");
        return request;
    }

    private User storedUser(String rawPassword, int status) {
        User user = new User();
        user.setId(7L);
        user.setUsername("testuser");
        user.setPassword(new BCryptPasswordEncoder().encode(rawPassword));
        user.setNickname("testuser");
        user.setStatus(status);
        return user;
    }

    @Test
    @DisplayName("注册成功 - 密码 BCrypt 落库且昵称默认用户名")
    void register_success_encodesPassword() {
        // given
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        });

        // when
        UserVO vo = userService.register(registerRequest());

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isNotEqualTo("123456");
        assertThat(new BCryptPasswordEncoder().matches("123456", saved.getPassword())).isTrue();
        assertThat(saved.getNickname()).isEqualTo("testuser");
        assertThat(saved.getRole()).isEqualTo(CommonConstants.ROLE_USER);
        assertThat(vo.getId()).isEqualTo(100L);
        assertThat(vo.getUsername()).isEqualTo("testuser");
        assertThat(vo.getRole()).isEqualTo(CommonConstants.ROLE_USER);
    }

    @Test
    @DisplayName("注册重名 - 抛 1001 且不落库")
    void register_duplicateUsername_throws1001() {
        // given
        when(userMapper.selectCount(any())).thenReturn(1L);

        // when / then
        assertThatThrownBy(() -> userService.register(registerRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.USERNAME_EXISTS.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册手机号重复 - 抛 1002")
    void register_duplicatePhone_throws1002() {
        // given：用户名查 0、手机号查 1
        when(userMapper.selectCount(any())).thenReturn(0L, 1L);
        RegisterRequest request = registerRequest();
        request.setPhone("13800000009");

        // when / then
        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PHONE_EXISTS.getCode());
    }

    @Test
    @DisplayName("登录成功 - 签发可解析 Token 且有效期秒数正确")
    void login_success_issuesParsableToken() {
        // given
        when(userMapper.selectOne(any())).thenReturn(storedUser("123456", 1));
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        // when
        LoginVO vo = userService.login(request);

        // then
        assertThat(vo.getToken()).isNotBlank();
        assertThat(JwtUtil.getUserId(SECRET, vo.getToken())).isEqualTo(7L);
        assertThat(vo.getExpiresIn()).isEqualTo(7200L);
        assertThat(vo.getUser().getUsername()).isEqualTo("testuser");
        assertThat(vo.getRole()).isEqualTo(CommonConstants.ROLE_USER);
    }

    @Test
    @DisplayName("管理员登录 - Token 携带 ADMIN 角色声明且 UserVO 返回 role")
    void login_whenAdmin_issuesAdminRoleClaim() {
        // given
        User admin = storedUser("123456", 1);
        admin.setRole(CommonConstants.ROLE_ADMIN);
        when(userMapper.selectOne(any())).thenReturn(admin);
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        // when
        LoginVO vo = userService.login(request);

        // then
        Claims claims = JwtUtil.parseToken(SECRET, vo.getToken());
        assertThat(claims.get(CommonConstants.CLAIM_ROLE, String.class)).isEqualTo(CommonConstants.ROLE_ADMIN);
        assertThat(vo.getUser().getRole()).isEqualTo(CommonConstants.ROLE_ADMIN);
        assertThat(vo.getRole()).isEqualTo(CommonConstants.ROLE_ADMIN);
    }

    @Test
    @DisplayName("历史账号 role 为空 - 登录兜底 USER 角色")
    void login_whenRoleMissing_defaultsToUser() {
        // given：role 字段为 null 的存量账号
        when(userMapper.selectOne(any())).thenReturn(storedUser("123456", 1));
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        // when
        LoginVO vo = userService.login(request);

        // then
        Claims claims = JwtUtil.parseToken(SECRET, vo.getToken());
        assertThat(claims.get(CommonConstants.CLAIM_ROLE, String.class)).isEqualTo(CommonConstants.ROLE_USER);
        assertThat(vo.getUser().getRole()).isEqualTo(CommonConstants.ROLE_USER);
        assertThat(vo.getRole()).isEqualTo(CommonConstants.ROLE_USER);
    }

    @Test
    @DisplayName("登录密码错误 - 抛 1003 不暴露账号存在性")
    void login_wrongPassword_throws1003() {
        // given
        when(userMapper.selectOne(any())).thenReturn(storedUser("123456", 1));
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrong-password");

        // when / then
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.LOGIN_FAILED.getCode());
    }

    @Test
    @DisplayName("登录账号不存在 - 同样抛 1003")
    void login_unknownUser_throws1003() {
        // given
        when(userMapper.selectOne(any())).thenReturn(null);
        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("123456");

        // when / then
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.LOGIN_FAILED.getCode());
    }

    @Test
    @DisplayName("登录禁用账号 - 抛 1004")
    void login_disabledUser_throws1004() {
        // given
        when(userMapper.selectOne(any())).thenReturn(storedUser("123456", 0));
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        // when / then
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.USER_DISABLED.getCode());
    }

    @Test
    @DisplayName("查不存在用户 - 抛 404")
    void getById_missing_throws404() {
        // given
        when(userMapper.selectById(999L)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> userService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("内部简要 - 解析逗号 ids 去重后仅返回非敏感字段")
    void listBriefs_parsesIdsAndReturnsBriefFields() {
        // given
        User user = storedUser("123456", 1);
        user.setUsername("zhangsan");
        user.setNickname("张三");
        user.setPhone("13812345678");
        when(userMapper.selectBatchIds(List.of(7L))).thenReturn(List.of(user));

        // when
        List<UserBriefVO> briefs = userService.listBriefs("7, 7");

        // then
        assertThat(briefs).hasSize(1);
        assertThat(briefs.get(0).getId()).isEqualTo(7L);
        assertThat(briefs.get(0).getUsername()).isEqualTo("zhangsan");
        assertThat(briefs.get(0).getNickname()).isEqualTo("张三");
    }

    @Test
    @DisplayName("内部简要 - 空 ids 直接返回空列表不查库")
    void listBriefs_whenBlank_returnsEmptyWithoutQuery() {
        assertThat(userService.listBriefs(" ")).isEmpty();
        verify(userMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("内部简要 - 非数字 ids 抛 400")
    void listBriefs_whenInvalidId_throwsBadRequest() {
        assertThatThrownBy(() -> userService.listBriefs("abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("内部简要 - 超过 50 个 ids 抛 400")
    void listBriefs_whenTooManyIds_throwsBadRequest() {
        String ids = LongStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));

        assertThatThrownBy(() -> userService.listBriefs(ids))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }
}