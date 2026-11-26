package com.hongcheng.order.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端订单视图脱敏边界测试：短手机号/空联系人原样返回，不产生错误掩码。
 */
class OrderAdminVOTest {

    @Test
    @DisplayName("手机号脱敏 - 保留前3后4，短号与空值原样返回")
    void maskPhone_keepsHeadTailAndShortValues() {
        assertThat(OrderAdminVO.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(OrderAdminVO.maskPhone("123")).isEqualTo("123");
        assertThat(OrderAdminVO.maskPhone(null)).isNull();
    }

    @Test
    @DisplayName("联系人脱敏 - 仅保留首字，空值原样返回")
    void maskName_keepsFirstCharOnly() {
        assertThat(OrderAdminVO.maskName("张三")).isEqualTo("张**");
        assertThat(OrderAdminVO.maskName("")).isEmpty();
        assertThat(OrderAdminVO.maskName(null)).isNull();
    }

    @Test
    @DisplayName("管理端视图 - 用户简要缺失时 username 为 null 而非匿名文案")
    void from_whenBriefMissing_leavesUsernameNull() {
        OrderVO vo = new OrderVO();
        vo.setUserId(1L);
        vo.setAppointmentPhone("13812345678");

        OrderAdminVO admin = OrderAdminVO.from(vo, null);

        assertThat(admin.getUsername()).isNull();
        assertThat(admin.getNickname()).isNull();
        assertThat(admin.getUserId()).isEqualTo(1L);
        assertThat(admin.getAppointmentPhone()).isEqualTo("138****5678");
    }
}