package com.hongcheng.order.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.OrderCreateStateVO;
import com.hongcheng.order.service.OrderService;
import com.hongcheng.order.service.OrderStateStore;
import com.hongcheng.order.service.OrderSubmitService;
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
 * OrderController 演示发货开关与状态轮询归属透传测试。
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderSubmitService orderSubmitService;
    @Mock
    private OrderService orderService;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderSubmitService, orderService);
    }

    @Test
    @DisplayName("hc.demo.enabled=false 时禁用演示发货返回 403")
    void demoDeliver_whenDemoDisabled_throws403() {
        ReflectionTestUtils.setField(controller, "demoEnabled", false);

        assertThatThrownBy(() -> controller.demoDeliver(1L, "HC100"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("state 接口向服务层透传 userId 与管理员标记")
    void state_forwardsUserIdAndAdminFlag() {
        OrderCreateStateVO vo = new OrderCreateStateVO("HC100", OrderStateStore.STATE_QUEUED, "排队中", null);
        when(orderService.state("HC100", 7L, false)).thenReturn(vo);
        when(orderService.state("HC100", 99L, true)).thenReturn(vo);

        assertThat(controller.state(7L, CommonConstants.ROLE_USER, "HC100").getData().getOrderNo())
                .isEqualTo("HC100");
        assertThat(controller.state(99L, CommonConstants.ROLE_ADMIN, "HC100").getData().getOrderNo())
                .isEqualTo("HC100");

        verify(orderService).state("HC100", 7L, false);
        verify(orderService).state("HC100", 99L, true);
    }
}
