package com.hongcheng.review.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.review.dto.ReviewImageVO;
import com.hongcheng.review.service.ReviewImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 评价晒图上传（需登录）：网关强制 JWT，X-User-Id 由网关注入。
 * 上传后返回 /api/review/uploads/** 公开地址。
 */
@Slf4j
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewUploadController {

    private final ReviewImageStorageService storageService;

    @PostMapping("/upload")
    public Result<ReviewImageVO> upload(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                        @RequestParam("file") MultipartFile file) {
        ReviewImageVO image = storageService.store(file);
        log.info("用户上传评价图片 userId={}, url={}, size={}", userId, image.getUrl(), image.getSize());
        return Result.ok(image);
    }
}
