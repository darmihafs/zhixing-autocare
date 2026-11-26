package com.hongcheng.ai.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongcheng.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单查询降级：返回 503 失败体。
 */
@Slf4j
@Component
public class OrderClientFallbackFactory implements FallbackFactory<OrderClient> {

    @Override
    public OrderClient create(Throwable cause) {
        return (userId, pageNum, pageSize) -> {
            log.warn("订单查询降级 userId={}, reason={}", userId, cause.toString());
            return Result.fail(503, "订单服务繁忙，请稍后重试");
        };
    }
}