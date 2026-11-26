package com.hongcheng.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理员模拟用户视角（Impersonation）配置。
 * <p>
 * expire-ms 为模拟 Token 短有效期（默认 15 分钟），远小于登录 Token 的 hc.jwt.expire-ms；
 * 模拟会话不签发 Refresh Token，到期即失效，需管理员重新发起。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hc.impersonation")
public class ImpersonationProperties {

    /** 模拟 Token 有效期毫秒 */
    private long expireMs = 900_000L;
}