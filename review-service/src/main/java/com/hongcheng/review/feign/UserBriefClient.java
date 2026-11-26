package com.hongcheng.review.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.review.dto.UserBriefVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务内部简要接口（/internal/** 不在网关暴露；由 Feign 拦截器附带 X-Internal-Token）。
 */
@FeignClient(name = "user-service", contextId = "userBriefClient", path = "/internal/user",
        fallbackFactory = UserBriefClientFallbackFactory.class)
public interface UserBriefClient {

    @GetMapping("/briefs")
    Result<List<UserBriefVO>> briefs(@RequestParam("ids") String ids);
}