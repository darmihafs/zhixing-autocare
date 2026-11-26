package com.hongcheng.pay.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 支付服务 Sentinel 规则：创建支付单入口 QPS 流控 + 慢调用熔断。
 * 资源名 {@code payCreate} 与 PayService#create 的 @SentinelResource 对齐。
 */
@Slf4j
@Configuration
public class PaySentinelConfig {

    @Bean
    public ApplicationRunner paySentinelRuleInitializer(
            @Value("${hc.sentinel.pay-create-qps:20}") double payCreateQps,
            @Value("${hc.sentinel.pay-degrade-rt-ms:1000}") double degradeRtMs,
            @Value("${hc.sentinel.pay-degrade-min-requests:5}") int degradeMinRequests,
            @Value("${hc.sentinel.pay-degrade-window-seconds:10}") int degradeWindowSeconds) {
        return args -> {
            FlowRule flow = new FlowRule("payCreate");
            flow.setLimitApp("default");
            flow.setGrade(RuleConstant.FLOW_GRADE_QPS);
            flow.setCount(payCreateQps);
            flow.setStrategy(RuleConstant.STRATEGY_DIRECT);
            flow.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            FlowRuleManager.loadRules(List.of(flow));

            DegradeRule degrade = new DegradeRule("payCreate");
            degrade.setGrade(RuleConstant.DEGRADE_GRADE_RT);
            degrade.setCount(degradeRtMs);
            degrade.setTimeWindow(degradeWindowSeconds);
            degrade.setMinRequestAmount(degradeMinRequests);
            degrade.setStatIntervalMs(10_000);
            DegradeRuleManager.loadRules(List.of(degrade));

            log.info("Sentinel 支付规则已装载 payCreate qps={}, degradeRtMs={}, minRequests={}, window={}s",
                    payCreateQps, degradeRtMs, degradeMinRequests, degradeWindowSeconds);
        };
    }
}
