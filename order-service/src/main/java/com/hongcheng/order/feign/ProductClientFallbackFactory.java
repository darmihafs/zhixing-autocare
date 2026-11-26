package com.hongcheng.order.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商品查询 Feign 降级：商品服务超时/不可用/被熔断时返回 503 失败体。
 * <p>
 * 不在 fallback 中直接抛异常：Sentinel 的 Feign 代理会把 fallback 抛出的异常
 * 包成 AssertionError，导致调用方无法按业务码区分“下游故障”和“商品下架”。
 * 返回 503 后由 OrderCreateProcessor 抛出业务异常，交给 MQ 重试。
 */
@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return id -> {
            log.warn("商品服务调用失败，触发 Sentinel/Feign 降级 productId={}, reason={}", id, cause.toString());
            return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "商品服务繁忙，请稍后重试");
        };
    }
}
