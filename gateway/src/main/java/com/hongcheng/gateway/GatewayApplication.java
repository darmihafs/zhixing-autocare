package com.hongcheng.gateway;

import com.hongcheng.common.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类（WebFlux 响应式栈，端口 8080）。
 * <p>
 * 统一入口：路由转发 + JWT 鉴权全局过滤器。
 * scanBasePackages 扫到 common 的全局异常处理器，但其 SERVLET 条件
 * 在响应式环境不成立，不会被加载。
 */
@SpringBootApplication(scanBasePackages = "com.hongcheng")
@EnableDiscoveryClient
@EnableConfigurationProperties(JwtProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}