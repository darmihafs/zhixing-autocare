package com.hongcheng.shop.controller;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.shop.dto.ShopVO;
import com.hongcheng.shop.service.SearchService;
import com.hongcheng.shop.service.ServiceItemService;
import com.hongcheng.shop.service.ShopService;
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
 * 门店直连库压测接口开关单测。
 */
@ExtendWith(MockitoExtension.class)
class ShopControllerTest {

    @Mock
    private ShopService shopService;
    @Mock
    private ServiceItemService serviceItemService;
    @Mock
    private SearchService searchService;

    private ShopController controller;

    @BeforeEach
    void setUp() {
        controller = new ShopController(shopService, serviceItemService, searchService);
    }

    @Test
    @DisplayName("hc.perf.enabled=false（默认）时拒绝直连 DB 压测接口")
    void dbEndpoints_whenPerfDisabled_throws403() {
        assertThatThrownBy(() -> controller.detailFromDb(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
        assertThatThrownBy(() -> controller.itemDetailFromDb(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
        verifyNoInteractions(shopService, serviceItemService);
    }

    @Test
    @DisplayName("hc.perf.enabled=true 时正常放行压测对照接口")
    void dbEndpoints_whenPerfEnabled_delegatesToService() {
        ReflectionTestUtils.setField(controller, "perfEnabled", true);
        ShopVO vo = new ShopVO();
        vo.setId(1L);
        when(shopService.detailFromDb(1L)).thenReturn(vo);

        assertThat(controller.detailFromDb(1L).getData().getId()).isEqualTo(1L);
        verify(shopService).detailFromDb(1L);
    }
}
