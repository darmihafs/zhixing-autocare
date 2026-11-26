package com.hongcheng.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类（端口 8084，库 hc_order）。
 * <p>
 * 职责：下单（RocketMQ 削峰）、订单状态机、延迟消息超时取消。
 */
@SpringBootApplication(scanBasePackages = "com.hongcheng")
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.hongcheng.order.mapper")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}