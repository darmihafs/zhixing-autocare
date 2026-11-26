package com.hongcheng.review.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.review.dto.CreateReviewRequest;
import com.hongcheng.review.dto.ReplyReviewRequest;
import com.hongcheng.review.dto.ReviewVO;
import com.hongcheng.review.service.ReviewService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价用户接口（经网关 JWT，X-User-Id 由网关注入）。
 */
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 发布评价（仅本人已完成订单；一单一品一评） */
    @PostMapping
    public Result<ReviewVO> create(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                   @Valid @RequestBody CreateReviewRequest request) {
        return Result.ok(reviewService.create(userId, request));
    }

    /** 我的评价 */
    @GetMapping("/my")
    public Result<Page<ReviewVO>> my(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                     @RequestParam(defaultValue = "1") long pageNum,
                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(reviewService.pageMine(userId, pageNum, pageSize));
    }

    /** 商家回复（角色权限控制留待管理端） */
    @PutMapping("/{id}/reply")
    public Result<ReviewVO> reply(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                  @PathVariable Long id,
                                  @Valid @RequestBody ReplyReviewRequest request) {
        return Result.ok(reviewService.reply(id, userId, request.getReply()));
    }
}
