package com.hongcheng.order.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.UserBriefVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户简要 Feign 降级：返回 503 失败体。
 * <p>
 * 管理端订单列表由 OrderService 识别 503 后抛业务异常（管理端不允许退化为匿名展示），
 * 不在 fallback 内抛异常（Sentinel 代理会把 fallback 的异常转成 AssertionError）。
 */
@Slf4j
@Component
public class UserBriefClientFallbackFactory implements FallbackFactory<UserBriefClient> {

    @Override
    public UserBriefClient create(Throwable cause) {
        return ids -> {
            log.warn("用户简要查询失败，触发 Sentinel/Feign 降级 ids={}, reason={}", ids, cause.toString());
            return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "用户服务繁忙，请稍后重试");
        };
    }
}