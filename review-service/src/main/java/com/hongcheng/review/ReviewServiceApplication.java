package com.hongcheng.review;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 评价服务启动类（端口 8086，库 hc_review）。
 * <p>
 * 职责：服务/商品评价、商家回复、点赞数异步同步（消费 user-service 点赞事件）。
 */
@SpringBootApplication(scanBasePackages = "com.hongcheng")
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.hongcheng.review.mapper")
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}