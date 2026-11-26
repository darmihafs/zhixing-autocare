package com.hongcheng.order.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 订单服务 Sentinel 规则：下单入口 QPS 流控 + 慢调用熔断。
 * <p>
 * 资源名 {@code orderCreate} 与 {@code OrderSubmitService#submit} 上的
 * {@code @SentinelResource} 保持一致；阈值可通过启动参数/Nacos 覆盖，
 * 方便做“限流触发”演示。规则只在启动时装载，修改后重启生效。
 */
@Slf4j
@Configuration
public class OrderSentinelConfig {

    @Bean
    public ApplicationRunner orderSentinelRuleInitializer(
            @Value("${hc.sentinel.order-create-qps:20}") double orderCreateQps,
            @Value("${hc.sentinel.order-degrade-rt-ms:1000}") double degradeRtMs,
            @Value("${hc.sentinel.order-degrade-min-requests:5}") int degradeMinRequests,
            @Value("${hc.sentinel.order-degrade-window-seconds:10}") int degradeWindowSeconds) {
        return args -> {
            FlowRule flow = new FlowRule("orderCreate");
            flow.setLimitApp("default");
            flow.setGrade(RuleConstant.FLOW_GRADE_QPS);
            flow.setCount(orderCreateQps);
            flow.setStrategy(RuleConstant.STRATEGY_DIRECT);
            flow.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            FlowRuleManager.loadRules(List.of(flow));

            DegradeRule degrade = new DegradeRule("orderCreate");
            degrade.setGrade(RuleConstant.DEGRADE_GRADE_RT);
            degrade.setCount(degradeRtMs);
            degrade.setTimeWindow(degradeWindowSeconds);
            degrade.setMinRequestAmount(degradeMinRequests);
            degrade.setStatIntervalMs(10_000);
            DegradeRuleManager.loadRules(List.of(degrade));

            log.info("Sentinel 订单规则已装载 orderCreate qps={}, degradeRtMs={}, minRequests={}, window={}s",
                    orderCreateQps, degradeRtMs, degradeMinRequests, degradeWindowSeconds);
        };
    }
}
