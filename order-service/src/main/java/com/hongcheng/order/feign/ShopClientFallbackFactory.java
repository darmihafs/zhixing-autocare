package com.hongcheng.order.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.ServiceItemInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 门店服务项目查询降级：门店服务超时/不可用时返回 503，由消费者抛出业务异常交给 MQ 重试。
 * <p>
 * 与 ProductClientFallbackFactory 相同，不在 fallback 中直接抛异常，避免被 Sentinel 包成 AssertionError。
 */
@Slf4j
@Component
public class ShopClientFallbackFactory implements FallbackFactory<ShopClient> {

    @Override
    public ShopClient create(Throwable cause) {
        return id -> {
            log.warn("门店服务调用失败，触发 Sentinel/Feign 降级 serviceItemId={}, reason={}",
                    id, cause.toString());
            return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "门店服务繁忙，请稍后重试");
        };
    }
}
