package com.hongcheng.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商品服务启动类（端口 8082，库 hc_product）。
 * <p>
 * 职责：分类/商品管理、库存扣减（UPDATE ... WHERE stock >= n 防超卖）。
 */
@SpringBootApplication(scanBasePackages = "com.hongcheng")
@EnableDiscoveryClient
@MapperScan("com.hongcheng.product.mapper")
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}