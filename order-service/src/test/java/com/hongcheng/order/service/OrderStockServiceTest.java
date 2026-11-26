package com.hongcheng.order.service;

import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.OrderItemRequest;
import com.hongcheng.order.dto.StockDeductRequest;
import com.hongcheng.order.dto.StockItem;
import com.hongcheng.order.entity.OrderItem;
import com.hongcheng.order.feign.ProductStockClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存操作测试：服务项目 itemType=2 不得进入商品库存扣减/回补。
 */
@ExtendWith(MockitoExtension.class)
class OrderStockServiceTest {

    @Mock
    private ProductStockClient productStockClient;

    @InjectMocks
    private OrderStockService orderStockService;

    @Test
    @DisplayName("转换库存请求 - 过滤服务项目，只保留商品明细")
    void toStockRequest_onlyKeepsProductItems() {
        OrderItem product = item(1, 1001L, 2);
        OrderItem service = item(2, 2001L, 1);

        StockDeductRequest request = orderStockService.toStockRequest(List.of(product, service));

        assertThat(request.getItems()).hasSize(1);
        assertThat(request.getItems().get(0).getProductId()).isEqualTo(1001L);
        assertThat(request.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("回补库存 - 纯服务订单不调用商品服务")
    void restoreWithRetry_whenOnlyServiceItems_skipsClient() {
        orderStockService.restoreWithRetry(List.of(item(2, 2001L, 1)), "HC900000000000000001");

        verify(productStockClient, never()).restore(any());
    }

    @Test
    @DisplayName("回补库存 - 混合订单只回补商品明细")
    void restoreWithRetry_whenMixedItems_restoresOnlyProducts() {
        when(productStockClient.restore(any())).thenReturn(Result.ok());

        orderStockService.restoreWithRetry(
                List.of(item(1, 1001L, 3), item(2, 2001L, 1)), "HC900000000000000002");

        ArgumentCaptor<StockDeductRequest> captor = ArgumentCaptor.forClass(StockDeductRequest.class);
        verify(productStockClient).restore(captor.capture());
        List<StockItem> items = captor.getValue().getItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductId()).isEqualTo(1001L);
        assertThat(items.get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("请求转换 - 无 productId 的服务项不会进入扣减请求")
    void toStockRequestFromRequests_ignoresServiceItems() {
        OrderItemRequest product = new OrderItemRequest();
        product.setProductId(1001L);
        product.setQuantity(1);
        OrderItemRequest service = new OrderItemRequest();
        service.setServiceItemId(2001L);
        service.setQuantity(1);

        StockDeductRequest request =
                orderStockService.toStockRequestFromRequests(List.of(product, service));

        assertThat(request.getItems()).hasSize(1);
        assertThat(request.getItems().get(0).getProductId()).isEqualTo(1001L);
    }

    private OrderItem item(int itemType, Long itemId, int quantity) {
        OrderItem item = new OrderItem();
        item.setItemType(itemType);
        item.setItemId(itemId);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal("10.00"));
        item.setTotalAmount(new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)));
        return item;
    }
}
