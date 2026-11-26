package com.hongcheng.ai.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工具 query_my_orders 的数据源：订单服务"我的订单"。
 * <p>
 * 仅在上游已确认登录态（X-User-Id 存在）时由 ToolExecutor 调用；
 * 该头由 ai-service 服务端注入，绝不接受客户端直接伪造。
 */
@FeignClient(name = "order-service", contextId = "aiOrderClient", path = "/order",
        fallbackFactory = OrderClientFallbackFactory.class)
public interface OrderClient {

    @GetMapping("/page")
    Result<JsonNode> page(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                          @RequestParam("pageNum") int pageNum,
                          @RequestParam("pageSize") int pageSize);
}