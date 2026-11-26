package com.hongcheng.order.config;

import com.hongcheng.common.constant.CommonConstants;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 全局配置：所有服务间调用附带内部凭证头。
 * <p>
 * 公开接口带该头无副作用；/internal/** 接口无该头会返回 401。
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(
            @Value("${hc.internal.token}") String internalToken) {
        return template -> template.header(CommonConstants.HEADER_INTERNAL_TOKEN, internalToken);
    }
}
