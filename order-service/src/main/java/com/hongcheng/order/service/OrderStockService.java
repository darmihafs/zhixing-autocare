package com.hongcheng.order.service;

import com.hongcheng.common.enums.OrderTypeEnum;
import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.OrderItemRequest;
import com.hongcheng.order.dto.StockDeductRequest;
import com.hongcheng.order.dto.StockItem;
import com.hongcheng.order.entity.OrderItem;
import com.hongcheng.order.feign.ProductStockClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单库存操作封装：Feign 调 product-service 内部接口。
 * <p>
 * 扣减沿用商品服务的原子 UPDATE（stock >= n），不会超卖；
 * 回补最多重试 3 次，仍失败只记 error 日志（需人工/后续补偿，见交付说明）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStockService {

    private static final int RESTORE_MAX_ATTEMPTS = 3;
    private static final long RESTORE_SLEEP_MILLIS = 200L;

    private final ProductStockClient productStockClient;

    /** 扣减库存（异常向上抛，由 MQ 重试；业务失败以 Result.code 返回） */
    public Result<Void> deduct(StockDeductRequest request) {
        return productStockClient.deduct(request);
    }

    /** 回补库存（订单取消/超时）：只回补商品明细，服务项目不经过商品库存；失败重试，不抛异常 */
    public void restoreWithRetry(List<OrderItem> items, String orderNo) {
        List<OrderItem> productItems = items == null ? List.of() : items.stream()
                .filter(this::isProductItem)
                .toList();
        if (productItems.isEmpty()) {
            log.info("订单无商品明细，跳过库存回补 orderNo={}", orderNo);
            return;
        }
        StockDeductRequest request = toStockRequest(productItems);
        for (int attempt = 1; attempt <= RESTORE_MAX_ATTEMPTS; attempt++) {
            try {
                Result<Void> result = productStockClient.restore(request);
                if (result != null && result.isSuccess()) {
                    log.info("库存回补成功 orderNo={}, attempt={}", orderNo, attempt);
                    return;
                }
                log.warn("库存回补失败 orderNo={}, attempt={}, message={}",
                        orderNo, attempt, result == null ? "null" : result.getMessage());
            } catch (Exception e) {
                log.warn("库存回补异常 orderNo={}, attempt={}, reason={}", orderNo, attempt, e.getMessage());
            }
            sleepQuietly();
        }
        log.error("库存回补最终失败，需人工补偿 orderNo={}, itemCount={}", orderNo, productItems.size());
    }

    /** 只把 itemType=1 的商品明细转换为库存操作，服务项目 itemType=2 不会误扣/误补商品库存 */
    public StockDeductRequest toStockRequest(List<OrderItem> items) {
        StockDeductRequest request = new StockDeductRequest();
        request.setItems(items.stream()
                .filter(this::isProductItem)
                .map(item -> new StockItem(item.getItemId(), item.getQuantity()))
                .toList());
        return request;
    }

    public StockDeductRequest toStockRequestFromRequests(List<OrderItemRequest> items) {
        StockDeductRequest request = new StockDeductRequest();
        request.setItems(items.stream()
                .filter(item -> item.getProductId() != null)
                .map(item -> new StockItem(item.getProductId(), item.getQuantity()))
                .toList());
        return request;
    }

    private boolean isProductItem(OrderItem item) {
        return item != null
                && Integer.valueOf(OrderTypeEnum.PRODUCT.getCode()).equals(item.getItemType());
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(RESTORE_SLEEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
