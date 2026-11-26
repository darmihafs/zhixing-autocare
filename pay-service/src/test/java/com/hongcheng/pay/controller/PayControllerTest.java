package com.hongcheng.pay.controller;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.pay.dto.PayTransactionVO;
import com.hongcheng.pay.service.PayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PayController 模拟支付演示开关单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PayControllerTest {

    @Mock
    private PayService payService;

    private PayController controller;

    @BeforeEach
    void setUp() {
        controller = new PayController(payService);
    }

    @Test
    @DisplayName("hc.demo.enabled=false 时禁用模拟支付返回 403")
    void mockPay_whenDemoDisabled_throws403() {
        ReflectionTestUtils.setField(controller, "demoEnabled", false);

        assertThatThrownBy(() -> controller.mockPay(1L, "PAY100"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
        verifyNoInteractions(payService);
    }

    @Test
    @DisplayName("hc.demo.enabled=true（默认）时放行模拟支付")
    void mockPay_whenDemoEnabled_delegatesToService() {
        PayTransactionVO vo = new PayTransactionVO();
        vo.setOutTradeNo("PAY100");
        when(payService.mockPay(1L, "PAY100")).thenReturn(vo);

        assertThat(controller.mockPay(1L, "PAY100").getData().getOutTradeNo()).isEqualTo("PAY100");
        verify(payService).mockPay(1L, "PAY100");
    }
}
