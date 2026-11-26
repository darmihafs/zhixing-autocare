package com.hongcheng.review.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.review.dto.OrderReviewContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单评价上下文 Feign 降级：返回 503 失败体，
 * ReviewService 识别后统一转成“订单服务暂不可用”，不误判为订单不可评价。
 * （Sentinel 代理会把 fallback 抛出的异常包成 AssertionError，故不在此抛异常。）
 */
@Slf4j
@Component
public class OrderReviewClientFallbackFactory implements FallbackFactory<OrderReviewClient> {

    @Override
    public OrderReviewClient create(Throwable cause) {
        return orderNo -> {
            log.warn("订单评价上下文调用失败，触发 Sentinel/Feign 降级 orderNo={}, reason={}",
                    orderNo, cause.toString());
            return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "订单服务繁忙，请稍后重试");
        };
    }
}
