package com.hongcheng.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LoadBalancer 子容器启动期预热。
 * <p>
 * 网关所有路由都是 lb://，首个请求会在 reactor-http-nio 线程上懒创建
 * LoadBalancer 子容器；子容器 refresh 发出的 ContextRefreshedEvent 会触发
 * WeightCalculatorWebFilter#onApplicationEvent 内的 blockLast()，
 * 在 reactor 线程上直接抛 IllegalStateException 导致首个请求 500
 * （/ws/order 首握手曾因此失败）。这里在启动主线程上预先创建各服务
 * 的子容器，把阻塞式初始化挪出请求链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadBalancerContextWarmer implements ApplicationRunner {

    /** 与 application.yml 路由中 lb:// 的服务名保持一致 */
    private static final List<String> SERVICE_IDS = List.of(
            "user-service", "product-service", "shop-service",
            "order-service", "pay-service", "review-service", "ai-service");

    private final LoadBalancerClientFactory loadBalancerClientFactory;

    @Override
    public void run(ApplicationArguments args) {
        for (String serviceId : SERVICE_IDS) {
            try {
                loadBalancerClientFactory.getInstance(serviceId);
                log.info("LoadBalancer 子容器预热完成 service={}", serviceId);
            } catch (Exception e) {
                log.warn("LoadBalancer 子容器预热失败 service={}, reason={}", serviceId, e.getMessage());
            }
        }
    }
}