package com.hongcheng.ai.config;

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
 * AI 对话入口 Sentinel 规则：QPS 流控 + 慢调用熔断。
 * 资源名 {@code aiChat} 与控制器 {@code @SentinelResource} 一致；
 * 触发限流/熔断时由 blockHandler/fallback 走本地检索兜底，不报 500。
 */
@Slf4j
@Configuration
public class AiSentinelConfig {

    @Bean
    public ApplicationRunner aiSentinelRuleInitializer(
            @Value("${hc.sentinel.ai-chat-qps:10}") double chatQps,
            @Value("${hc.sentinel.ai-degrade-rt-ms:15000}") double degradeRtMs,
            @Value("${hc.sentinel.ai-degrade-min-requests:5}") int degradeMinRequests,
            @Value("${hc.sentinel.ai-degrade-window-seconds:10}") int degradeWindowSeconds) {
        return args -> {
            FlowRule flow = new FlowRule("aiChat");
            flow.setLimitApp("default");
            flow.setGrade(RuleConstant.FLOW_GRADE_QPS);
            flow.setCount(chatQps);
            flow.setStrategy(RuleConstant.STRATEGY_DIRECT);
            flow.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            FlowRuleManager.loadRules(List.of(flow));

            DegradeRule degrade = new DegradeRule("aiChat");
            degrade.setGrade(RuleConstant.DEGRADE_GRADE_RT);
            degrade.setCount(degradeRtMs);
            degrade.setTimeWindow(degradeWindowSeconds);
            degrade.setMinRequestAmount(degradeMinRequests);
            degrade.setStatIntervalMs(10_000);
            DegradeRuleManager.loadRules(List.of(degrade));

            log.info("Sentinel AI 规则已装载 aiChat qps={}, degradeRtMs={}, minRequests={}, window={}s",
                    chatQps, degradeRtMs, degradeMinRequests, degradeWindowSeconds);
        };
    }
}