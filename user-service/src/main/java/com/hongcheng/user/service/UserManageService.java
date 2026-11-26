package com.hongcheng.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.config.JwtProperties;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.common.util.JwtUtil;
import com.hongcheng.user.config.ImpersonationProperties;
import com.hongcheng.user.dto.ImpersonationVO;
import com.hongcheng.user.dto.UserAdminVO;
import com.hongcheng.user.dto.UserVO;
import com.hongcheng.user.entity.User;
import com.hongcheng.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端用户管理与管理员模拟用户视角（User Impersonation）。
 * <p>
 * 安全约束：仅 ADMIN 会话可发起（/user/manage/** 由 AdminRoleInterceptor 校验）；
 * 目标必须存在、启用、role=USER 且非管理员自身；模拟 Token 短有效期、role 固定 USER、
 * 携带 impersonatorId/impersonatedBy 声明，不签发 Refresh Token；
 * 开始/退出均写审计日志。退出仅恢复前端保存的原管理员 Token，不重新签发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageService {

    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;
    private final ImpersonationProperties impersonationProperties;

    /** 管理端用户分页：真实用户名/昵称 + 打码手机号；keyword 可选模糊匹配用户名/昵称 */
    public Page<UserAdminVO> pageForAdmin(String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getUsername, keyword.trim())
                        .or()
                        .like(User::getNickname, keyword.trim()))
                .orderByDesc(User::getCreateTime);
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<UserAdminVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(UserAdminVO::from).toList());
        return voPage;
    }

    /** 发起模拟：校验目标后签发短效模拟 Token 并写审计日志 */
    public ImpersonationVO impersonate(Long adminId, String adminUsername, Long targetId) {
        if (targetId.equals(adminId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能模拟管理员自身视角");
        }
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (Integer.valueOf(0).equals(target.getStatus())) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }
        if (CommonConstants.ROLE_ADMIN.equals(target.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "禁止模拟其他管理员");
        }
        long expireMs = impersonationProperties.getExpireMs();
        String token = JwtUtil.createImpersonationToken(jwtProperties.getSecret(), expireMs,
                target.getId(), target.getUsername(), adminId, adminUsername);
        ImpersonationVO vo = new ImpersonationVO();
        vo.setToken(token);
        vo.setExpiresIn(expireMs / 1000);
        vo.setImpersonatorId(adminId);
        vo.setImpersonatedBy(adminUsername);
        vo.setUser(toVO(target));
        log.info("IMPERSONATION_START impersonatorId={} impersonatedBy={} targetId={} targetUsername={} expireMs={}",
                adminId, adminUsername, target.getId(), target.getUsername(), expireMs);
        return vo;
    }

    /** 退出模拟：仅审计记录；身份恢复由前端还原原管理员 Token 完成，不重新签发 */
    public void exitImpersonation(Long impersonatorId, Long targetId) {
        if (impersonatorId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前不处于模拟视角");
        }
        log.info("IMPERSONATION_EXIT impersonatorId={} targetId={}", impersonatorId, targetId);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setRole(StringUtils.hasText(user.getRole()) ? user.getRole() : CommonConstants.ROLE_USER);
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}