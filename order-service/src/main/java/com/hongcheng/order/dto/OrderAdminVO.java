package com.hongcheng.order.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

/**
 * 管理端订单视图：在 OrderVO 基础上补全真实 username/nickname（来自用户内部简要接口）。
 * <p>
 * 预约手机号/联系人等敏感字段保留部分脱敏；C 端 OrderVO 与本人订单接口不受影响。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderAdminVO extends OrderVO {

    /** 真实用户名（管理端可见，不再使用「匿名用户/用户+数字」） */
    private String username;

    /** 真实昵称 */
    private String nickname;

    public static OrderAdminVO from(OrderVO vo, UserBriefVO user) {
        OrderAdminVO admin = new OrderAdminVO();
        BeanUtils.copyProperties(vo, admin);
        if (user != null) {
            admin.setUsername(user.getUsername());
            admin.setNickname(user.getNickname());
        }
        admin.setAppointmentPhone(maskPhone(vo.getAppointmentPhone()));
        admin.setAppointmentContact(maskName(vo.getAppointmentContact()));
        return admin;
    }

    /** 手机号保留前 3 后 4，如 138****5678；不足 7 位原样返回 */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 联系人仅保留首字，如 张**；空值原样返回 */
    static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.charAt(0) + "**";
    }
}