package com.hongcheng.review.config;

import com.hongcheng.common.constant.CommonConstants;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 全局配置：调用 order-service /internal/** 时附带内部凭证头。
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor(
            @Value("${hc.internal.token}") String internalToken) {
        return template -> template.header(CommonConstants.HEADER_INTERNAL_TOKEN, internalToken);
    }
}
