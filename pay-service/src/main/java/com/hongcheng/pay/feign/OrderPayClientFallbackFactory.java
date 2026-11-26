package com.hongcheng.pay.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.pay.dto.OrderPayInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单服务 Feign 降级：订单服务不可用/超时/熔断时返回 503 失败体。
 * <p>
 * 注意不能在这里直接抛 BusinessException：Sentinel 的 Feign 代理会把
 * fallback 方法抛出的受检结果包成 InvocationTargetException，再转成
 * AssertionError 抛给调用方，业务异常语义和 HTTP 状态都会丢失。
 * 因此统一返回 {@code Result.fail(503)}，由调用方按 code 转成业务异常。
 */
@Slf4j
@Component
public class OrderPayClientFallbackFactory implements FallbackFactory<OrderPayClient> {

    @Override
    public OrderPayClient create(Throwable cause) {
        return new OrderPayClient() {
            @Override
            public Result<OrderPayInfo> detail(String orderNo) {
                log.warn("订单服务调用失败，触发 Sentinel/Feign 降级 orderNo={}, reason={}",
                        orderNo, cause.toString());
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "订单服务繁忙，请稍后重试");
            }

            @Override
            public Result<Void> pay(String orderNo) {
                log.warn("订单支付回调失败，触发 Sentinel/Feign 降级 orderNo={}, reason={}",
                        orderNo, cause.toString());
                return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "订单服务繁忙，请稍后重试");
            }
        };
    }
}
