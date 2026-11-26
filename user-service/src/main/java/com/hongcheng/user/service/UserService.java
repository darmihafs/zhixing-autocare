package com.hongcheng.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongcheng.common.config.JwtProperties;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.common.util.JwtUtil;
import com.hongcheng.user.dto.LoginRequest;
import com.hongcheng.user.dto.LoginVO;
import com.hongcheng.user.dto.RegisterRequest;
import com.hongcheng.user.dto.UpdateProfileRequest;
import com.hongcheng.user.dto.UserBriefVO;
import com.hongcheng.user.dto.UserVO;
import com.hongcheng.user.entity.User;
import com.hongcheng.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 用户注册/登录/查询。
 * <p>
 * 密码 BCrypt 存储；登录失败统一提示「用户名或密码错误」，不暴露账号是否存在。
 * 唯一性先查后插，并发场景由 uk_username/uk_phone 唯一索引兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;

    /** 注册：成功返回用户信息（不含密码），不自动登录 */
    public UserVO register(RegisterRequest request) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        boolean hasPhone = StringUtils.hasText(request.getPhone());
        if (hasPhone && userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone())) > 0) {
            throw new BusinessException(ResultCode.PHONE_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setPhone(hasPhone ? request.getPhone() : null);
        user.setGender(0);
        user.setStatus(1);
        user.setRole(CommonConstants.ROLE_USER);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("注册唯一索引冲突 username={}, phone={}", request.getUsername(), maskPhone(request.getPhone()));
            boolean usernameTaken = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, request.getUsername())) > 0;
            throw new BusinessException(usernameTaken ? ResultCode.USERNAME_EXISTS : ResultCode.PHONE_EXISTS);
        }
        log.info("用户注册成功 userId={}, username={}", user.getId(), user.getUsername());
        return toVO(user);
    }

    /** 登录：校验密码与状态后签发 JWT */
    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        String token = JwtUtil.createToken(jwtProperties.getSecret(), jwtProperties.getExpireMs(),
                user.getId(), user.getUsername(), roleOf(user));
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setExpiresIn(jwtProperties.getExpireMs() / 1000);
        vo.setRole(roleOf(user));
        vo.setUser(toVO(user));
        log.info("用户登录成功 userId={}, username={}", user.getId(), user.getUsername());
        return vo;
    }

    /** 按ID查用户视图（/user/me 与后续 Feign 调用共用） */
    public UserVO getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toVO(user);
    }

    /** 修改个人资料：只更新请求中显式提供的字段；手机号仍需全局唯一 */
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (request.getNickname() != null) {
            if (!StringUtils.hasText(request.getNickname())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "昵称不能为空");
            }
            user.setNickname(request.getNickname().trim());
        }
        if (request.getPhone() != null) {
            if (StringUtils.hasText(request.getPhone())) {
                boolean phoneTaken = userMapper.selectCount(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, request.getPhone())
                        .ne(User::getId, userId)) > 0;
                if (phoneTaken) {
                    throw new BusinessException(ResultCode.PHONE_EXISTS);
                }
                user.setPhone(request.getPhone());
            } else {
                user.setPhone(null);
            }
        }
        if (request.getAvatar() != null) {
            user.setAvatar(StringUtils.hasText(request.getAvatar()) ? request.getAvatar().trim() : null);
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        try {
            userMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.PHONE_EXISTS);
        }
        log.info("用户资料更新成功 userId={}", userId);
        return toVO(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setRole(roleOf(user));
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    /** 内部批量简要：仅返回 id/username/nickname，供管理端补全真实用户名（不含手机号等敏感字段） */
    public List<UserBriefVO> listBriefs(String ids) {
        if (!StringUtils.hasText(ids)) {
            return List.of();
        }
        List<Long> idList;
        try {
            idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::valueOf)
                    .distinct()
                    .toList();
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ids 必须为逗号分隔的数字用户ID");
        }
        if (idList.isEmpty()) {
            return List.of();
        }
        if (idList.size() > 50) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "ids 单次最多 50 个");
        }
        return userMapper.selectBatchIds(idList).stream().map(UserBriefVO::from).toList();
    }

    /** 角色兜底：历史数据 role 为空时视为普通用户 */
    private String roleOf(User user) {
        return StringUtils.hasText(user.getRole()) ? user.getRole() : CommonConstants.ROLE_USER;
    }

    /** 手机号日志掩码脱敏，避免明文落盘 */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "";
        }
        String trimmed = phone.trim();
        if (trimmed.length() < 7) {
            return "***";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}