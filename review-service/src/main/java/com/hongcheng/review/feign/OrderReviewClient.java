package com.hongcheng.review.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.review.dto.OrderReviewContext;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 订单服务内部接口（带 X-Internal-Token；网关不路由 /internal/**）。
 */
@FeignClient(name = "order-service", contextId = "orderReviewClient", path = "/internal/order",
        fallbackFactory = OrderReviewClientFallbackFactory.class)
public interface OrderReviewClient {

    /** 查询订单归属、状态与明细快照，用于评价资格校验 */
    @GetMapping("/{orderNo}/review-context")
    Result<OrderReviewContext> reviewContext(@PathVariable("orderNo") String orderNo);
}
