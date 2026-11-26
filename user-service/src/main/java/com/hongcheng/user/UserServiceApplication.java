package com.hongcheng.user;

import com.hongcheng.common.config.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类（端口 8081，库 hc_user）。
 * <p>
 * 职责：注册/登录（BCrypt + JWT）、关注、点赞。
 */
@SpringBootApplication(scanBasePackages = "com.hongcheng")
@EnableDiscoveryClient
@EnableConfigurationProperties(JwtProperties.class)
@MapperScan("com.hongcheng.user.mapper")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}