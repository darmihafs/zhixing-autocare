package com.hongcheng.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 洪兴智能养车顾问（AI Agent）。
 * <p>
 * 轻量服务：不接 MySQL / RocketMQ / ES，仅通过 OpenFeign 复用现有
 * 商品 / 门店 / 订单微服务的真实数据，对外提供 SSE 流式导购对话。
 */
@SpringBootApplication(scanBasePackages = "com.hongcheng")
@EnableDiscoveryClient
@EnableFeignClients
@ConfigurationPropertiesScan
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}