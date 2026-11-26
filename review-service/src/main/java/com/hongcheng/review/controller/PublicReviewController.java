package com.hongcheng.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.result.Result;
import com.hongcheng.review.dto.ReviewStatsVO;
import com.hongcheng.review.dto.ReviewVO;
import com.hongcheng.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价公开浏览接口（网关匿名放行）：商品/服务评价列表、聚合统计、详情、门店评价。
 */
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewService reviewService;

    /** 商品/服务项目评价分页（可按星级筛选） */
    @GetMapping("/item/{itemType}/{itemId}")
    public Result<Page<ReviewVO>> pageByItem(@PathVariable Integer itemType,
                                             @PathVariable Long itemId,
                                             @RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             @RequestParam(required = false) Integer score) {
        return Result.ok(reviewService.pageByItem(itemType, itemId, pageNum, pageSize, score));
    }

    /** 商品/服务项目评分聚合：平均分、总数、1~5 星分布 */
    @GetMapping("/item/{itemType}/{itemId}/stats")
    public Result<ReviewStatsVO> stats(@PathVariable Integer itemType,
                                       @PathVariable Long itemId) {
        return Result.ok(reviewService.stats(itemType, itemId));
    }

    /** 门店评价分页 */
    @GetMapping("/shop/{shopId}")
    public Result<Page<ReviewVO>> pageByShop(@PathVariable Long shopId,
                                             @RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(reviewService.pageByShop(shopId, pageNum, pageSize));
    }

    /** 门店评分聚合：平均分、总数、1~5 星分布 */
    @GetMapping("/shop/{shopId}/stats")
    public Result<ReviewStatsVO> shopStats(@PathVariable Long shopId) {
        return Result.ok(reviewService.statsByShop(shopId));
    }

    /** 评价详情 */
    @GetMapping("/detail/{id}")
    public Result<ReviewVO> detail(@PathVariable Long id) {
        return Result.ok(reviewService.detail(id));
    }
}
