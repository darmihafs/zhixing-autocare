package com.hongcheng.pay.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.pay.dto.OrderPayInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 订单服务内部接口（带 X-Internal-Token；网关不路由 /internal/**）。
 */
@FeignClient(name = "order-service", contextId = "orderPayClient", path = "/internal/order",
        fallbackFactory = OrderPayClientFallbackFactory.class)
public interface OrderPayClient {

    /** 查询订单归属、应付金额与状态 */
    @GetMapping("/{orderNo}")
    Result<OrderPayInfo> detail(@PathVariable("orderNo") String orderNo);

    /** 支付成功回调：订单 0待支付 → 10已支付（重复调用幂等） */
    @PostMapping("/{orderNo}/pay")
    Result<Void> pay(@PathVariable("orderNo") String orderNo);
}
