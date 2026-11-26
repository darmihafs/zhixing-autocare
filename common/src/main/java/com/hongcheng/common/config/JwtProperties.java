package com.hongcheng.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（hc.jwt.*）。
 * <p>
 * gateway 与 user-service 各自 @EnableConfigurationProperties 引入，
 * 两侧 secret 必须一致（开发环境写在各自 application.yml，生产走 Nacos 配置中心）。
 */
@Data
@ConfigurationProperties(prefix = "hc.jwt")
public class JwtProperties {

    /** HS256 签名密钥，至少 32 字节 */
    private String secret;

    /** Token 有效期（毫秒），默认 2 小时 */
    private long expireMs = 7_200_000L;
}