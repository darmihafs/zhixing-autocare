package com.hongcheng.user.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 配置：受保护路径挂登录态拦截器（/auth/** 为白名单不挂）；
 * /user/manage/** 追加 ADMIN 角色拦截器（用户管理/模拟视角仅真实管理员会话）；
 * /internal/** 挂内部凭证拦截器（仅服务间 Feign 可调）。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthHeaderInterceptor authHeaderInterceptor;
    private final AdminRoleInterceptor adminRoleInterceptor;
    private final InternalTokenInterceptor internalTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authHeaderInterceptor)
                .addPathPatterns("/user/**", "/follow/**", "/like/**");
        registry.addInterceptor(adminRoleInterceptor)
                .addPathPatterns("/user/manage/**");
        registry.addInterceptor(internalTokenInterceptor)
                .addPathPatterns("/internal/**");
    }
}