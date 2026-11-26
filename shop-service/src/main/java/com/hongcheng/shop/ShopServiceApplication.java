package com.hongcheng.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 门店服务启动类（端口 8083，库 hc_shop）。
 * <p>
 * 职责：门店/养车服务项目管理、ES(IK) 搜索、Redis 缓存。
 */
@SpringBootApplication(scanBasePackages = "com.hongcheng")
@EnableDiscoveryClient
@MapperScan("com.hongcheng.shop.mapper")
public class ShopServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class, args);
    }
}