package com.hongcheng.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改请求：字段为 null 表示不修改；phone 传空串表示解绑手机号。
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 50, message = "昵称不能超过50字")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 255, message = "头像地址不能超过255字")
    private String avatar;

    @Min(value = 0, message = "性别取值不合法")
    @Max(value = 2, message = "性别取值不合法")
    private Integer gender;
}
