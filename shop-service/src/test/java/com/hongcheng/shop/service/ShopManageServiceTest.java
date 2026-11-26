package com.hongcheng.shop.service;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.shop.dto.ServiceItemSaveRequest;
import com.hongcheng.shop.dto.ShopSaveRequest;
import com.hongcheng.shop.dto.ShopSyncMessage;
import com.hongcheng.shop.entity.ServiceItem;
import com.hongcheng.shop.entity.Shop;
import com.hongcheng.shop.mapper.ServiceItemMapper;
import com.hongcheng.shop.mapper.ShopMapper;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端写操作单元测试：落库成功 -> 删缓存 -> RocketMQ 异步发同步消息；异常分支不发消息。
 */
class ShopManageServiceTest {

    private ShopMapper shopMapper;
    private ServiceItemMapper serviceItemMapper;
    private ShopCacheService shopCacheService;
    private RocketMQTemplate rocketMQTemplate;
    private ShopManageService shopManageService;

    @BeforeEach
    void setUp() {
        shopMapper = mock(ShopMapper.class);
        serviceItemMapper = mock(ServiceItemMapper.class);
        shopCacheService = mock(ShopCacheService.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        shopManageService = new ShopManageService(shopMapper, serviceItemMapper, shopCacheService, rocketMQTemplate);
    }

    @Test
    @DisplayName("创建门店 - 默认营业/初始评分销量，并异步发送 SHOP 同步消息")
    void createShop_success_sendsSyncMessage() {
        // given
        when(shopMapper.insert(any(Shop.class))).thenAnswer(invocation -> {
            Shop shop = invocation.getArgument(0);
            shop.setId(100L);
            return 1;
        });
        ShopSaveRequest request = buildShopRequest("洪城养车·验收临时店");

        // when
        Long id = shopManageService.createShop(request);

        // then
        assertThat(id).isEqualTo(100L);
        verify(shopMapper).insert(any(Shop.class));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rocketMQTemplate).asyncSend(eq("HC_SHOP_SYNC:SHOP"), payload.capture(), any(SendCallback.class));
        assertThat(payload.getValue()).isInstanceOf(ShopSyncMessage.class);
        ShopSyncMessage sent = (ShopSyncMessage) payload.getValue();
        assertThat(sent.getType()).isEqualTo("SHOP");
        assertThat(sent.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("修改门店 - 删缓存并异步发送同步消息")
    void updateShop_success_evictsCacheAndSendsMessage() {
        // given
        Shop existing = new Shop();
        existing.setId(1L);
        existing.setShopName("旧名称");
        when(shopMapper.selectById(1L)).thenReturn(existing);

        // when
        shopManageService.updateShop(1L, buildShopRequest("洪城养车·新名称"));

        // then
        verify(shopMapper).updateById(any(Shop.class));
        verify(shopCacheService).evict("shop:detail:1");
        verify(rocketMQTemplate).asyncSend(eq("HC_SHOP_SYNC:SHOP"),
                org.mockito.ArgumentMatchers.<Object>any(), any(SendCallback.class));
    }

    @Test
    @DisplayName("修改不存在的门店 - 抛 1201，不删缓存不发消息")
    void updateShop_notFound_throwsWithoutSideEffects() {
        // given
        when(shopMapper.selectById(999L)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> shopManageService.updateShop(999L, buildShopRequest("任意")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(1201);
        verify(shopCacheService, never()).evict(anyString());
        verify(rocketMQTemplate, never()).asyncSend(anyString(),
                org.mockito.ArgumentMatchers.<Object>any(), any(SendCallback.class));
    }

    @Test
    @DisplayName("非法状态值 - 抛 400，不更新不发消息")
    void changeShopStatus_invalidStatus_throwsBadRequest() {
        // when / then
        assertThatThrownBy(() -> shopManageService.changeShopStatus(1L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(400);
        verify(shopMapper, never()).updateById(any(Shop.class));
    }

    @Test
    @DisplayName("创建服务项目 - 门店不存在抛 1201；成功则发送 SERVICE_ITEM 消息")
    void createServiceItem_validatesShopAndSendsMessage() {
        // given
        when(shopMapper.selectById(999L)).thenReturn(null);
        ServiceItemSaveRequest bad = buildItemRequest(999L);
        assertThatThrownBy(() -> shopManageService.createServiceItem(bad))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(1201);
        verify(serviceItemMapper, never()).insert(any(ServiceItem.class));

        Shop shop = new Shop();
        shop.setId(1L);
        when(shopMapper.selectById(1L)).thenReturn(shop);
        when(serviceItemMapper.insert(any(ServiceItem.class))).thenAnswer(invocation -> {
            ServiceItem item = invocation.getArgument(0);
            item.setId(200L);
            return 1;
        });

        // when
        Long id = shopManageService.createServiceItem(buildItemRequest(1L));

        // then
        assertThat(id).isEqualTo(200L);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rocketMQTemplate).asyncSend(eq("HC_SHOP_SYNC:SERVICE_ITEM"), payload.capture(),
                any(SendCallback.class));
        assertThat(payload.getValue()).isInstanceOf(ShopSyncMessage.class);
        ShopSyncMessage sent = (ShopSyncMessage) payload.getValue();
        assertThat(sent.getType()).isEqualTo("SERVICE_ITEM");
        assertThat(sent.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("服务项目下架 - 删服务详情缓存并发消息（ES 侧 status 过滤后不可搜）")
    void changeServiceItemStatus_evictsItemCache() {
        // given
        ServiceItem item = new ServiceItem();
        item.setId(7L);
        when(serviceItemMapper.selectById(7L)).thenReturn(item);

        // when
        shopManageService.changeServiceItemStatus(7L, 0);

        // then
        assertThat(item.getStatus()).isZero();
        verify(shopCacheService).evict("shop:item:7");
        verify(rocketMQTemplate).asyncSend(eq("HC_SHOP_SYNC:SERVICE_ITEM"),
                org.mockito.ArgumentMatchers.<Object>any(), any(SendCallback.class));
    }

    private ShopSaveRequest buildShopRequest(String shopName) {
        ShopSaveRequest request = new ShopSaveRequest();
        request.setShopName(shopName);
        request.setCity("南昌市");
        request.setDistrict("青山湖区");
        request.setAddress("南京东路 999 号");
        request.setDescription("验收临时门店");
        return request;
    }

    private ServiceItemSaveRequest buildItemRequest(Long shopId) {
        ServiceItemSaveRequest request = new ServiceItemSaveRequest();
        request.setShopId(shopId);
        request.setItemName("验收临时服务");
        request.setCategory("保养");
        request.setPrice(new BigDecimal("1.00"));
        request.setDurationMin(30);
        return request;
    }
}