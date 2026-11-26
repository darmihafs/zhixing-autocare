package com.hongcheng.order.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.StockDeductRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存 Feign 降级：返回 503 失败体。
 * <p>
 * 扣减由 OrderCreateProcessor 识别 503 后抛业务异常触发 MQ 重试；
 * 回补由 OrderStockService.restoreWithRetry 按失败重试，均不依赖 fallback 抛异常
 * （Sentinel 代理会把 fallback 的异常转成 AssertionError）。
 */
@Slf4j
@Component
public class ProductStockClientFallbackFactory implements FallbackFactory<ProductStockClient> {

    @Override
    public ProductStockClient create(Throwable cause) {
        return new ProductStockClient() {
            @Override
            public Result<Void> deduct(StockDeductRequest request) {
                log.warn("库存扣减调用失败，触发 Sentinel/Feign 降级 items={}, reason={}",
                        request == null ? 0 : request.getItems().size(), cause.toString());
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "库存服务繁忙，请稍后重试");
            }

            @Override
            public Result<Void> restore(StockDeductRequest request) {
                log.warn("库存回补调用失败，触发 Sentinel/Feign 降级 items={}, reason={}",
                        request == null ? 0 : request.getItems().size(), cause.toString());
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "库存服务繁忙，请稍后重试");
            }
        };
    }
}
