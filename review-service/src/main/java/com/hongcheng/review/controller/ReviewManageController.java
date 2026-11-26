package com.hongcheng.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.result.Result;
import com.hongcheng.review.dto.ReviewAdminVO;
import com.hongcheng.review.service.ReviewService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价管理员接口（/review/manage/** 由 AdminTokenInterceptor 校验 ADMIN 角色）。
 * <p>
 * 与 C 端公开评价列表分离：仅管理端可见真实用户名/昵称。
 */
@Validated
@RestController
@RequestMapping("/review/manage")
@RequiredArgsConstructor
public class ReviewManageController {

    private final ReviewService reviewService;

    /** 管理员评价分页（shopId 可选，空为全部门店）；含真实用户名/昵称 */
    @GetMapping("/page")
    public Result<Page<ReviewAdminVO>> page(@RequestParam(required = false) Long shopId,
                                            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
                                            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.ok(reviewService.adminPage(shopId, pageNum, pageSize));
    }
}