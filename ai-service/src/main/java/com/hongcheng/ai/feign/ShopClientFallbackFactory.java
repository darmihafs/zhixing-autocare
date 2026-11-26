package com.hongcheng.ai.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongcheng.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 门店/服务检索降级：返回 503 失败体。
 */
@Slf4j
@Component
public class ShopClientFallbackFactory implements FallbackFactory<ShopClient> {

    @Override
    public ShopClient create(Throwable cause) {
        return new ShopClient() {
            @Override
            public Result<JsonNode> searchShops(String keyword, int pageNum, int pageSize) {
                log.warn("门店检索降级 keyword={}, reason={}", keyword, cause.toString());
                return Result.fail(503, "门店服务繁忙，请稍后重试");
            }

            @Override
            public Result<JsonNode> searchServices(String keyword, int pageNum, int pageSize) {
                log.warn("服务检索降级 keyword={}, reason={}", keyword, cause.toString());
                return Result.fail(503, "门店服务繁忙，请稍后重试");
            }
        };
    }
}