package com.hongcheng.ai.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongcheng.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商品检索降级：返回 503 失败体（不抛异常，避免被包成 AssertionError）。
 */
@Slf4j
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        return (keyword, pageNum, pageSize) -> {
            log.warn("商品服务检索降级 keyword={}, reason={}", keyword, cause.toString());
            return Result.fail(503, "商品服务繁忙，请稍后重试");
        };
    }
}