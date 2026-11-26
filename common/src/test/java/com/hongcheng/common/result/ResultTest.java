package com.hongcheng.common.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Result 统一返回体单元测试。
 */
class ResultTest {

    @Test
    @DisplayName("ok(data) - code=200 且携带数据")
    void ok_withData_carriesPayload() {
        // when
        Result<String> result = Result.ok("hongcheng");

        // then
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMessage()).isEqualTo("操作成功");
        assertThat(result.getData()).isEqualTo("hongcheng");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("ok() - 无数据时 data 为 null 仍成功")
    void ok_withoutData_isSuccess() {
        // when
        Result<Void> result = Result.ok();

        // then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("fail(ResultCode) - 码与提示取自枚举且 data 为 null")
    void fail_withResultCode_usesEnumValues() {
        // when
        Result<Void> result = Result.fail(ResultCode.UNAUTHORIZED);

        // then
        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMessage()).isEqualTo("未登录或登录已过期");
        assertThat(result.getData()).isNull();
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("fail(code,message) - 支持业务自定义码")
    void fail_withCustomCode_keepsValues() {
        // when
        Result<Void> result = Result.fail(1301, "订单状态不允许取消");

        // then
        assertThat(result.getCode()).isEqualTo(1301);
        assertThat(result.getMessage()).isEqualTo("订单状态不允许取消");
        assertThat(result.isSuccess()).isFalse();
    }
}