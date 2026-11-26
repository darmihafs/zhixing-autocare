package com.hongcheng.user.dto;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.user.entity.User;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 管理端用户列表视图：真实 username/nickname/userId，仅手机号部分打码。
 * <p>
 * 不含密码/逻辑删除标记；与 C 端匿名展示（前端 maskUser）完全分离。
 */
@Data
public class UserAdminVO {

    private Long id;

    private String username;

    private String nickname;

    /** 部分打码：138****5678；不足 7 位原样返回 */
    private String phone;

    /** 角色：USER/ADMIN（历史空值兜底 USER） */
    private String role;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;

    public static UserAdminVO from(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setRole(StringUtils.hasText(user.getRole()) ? user.getRole() : CommonConstants.ROLE_USER);
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}