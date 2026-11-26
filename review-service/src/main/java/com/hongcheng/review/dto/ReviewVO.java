package com.hongcheng.review.dto;

import com.hongcheng.review.entity.Review;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图（images 由 JSON 字符串解析为列表）。
 */
@Data
public class ReviewVO {

    private Long id;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private Integer itemType;

    private Long itemId;

    private Integer score;

    private String content;

    private List<String> images;

    private String reply;

    private LocalDateTime replyTime;

    private Integer likeCount;

    private LocalDateTime createTime;

    public static ReviewVO from(Review review, List<String> images) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setOrderNo(review.getOrderNo());
        vo.setUserId(review.getUserId());
        vo.setShopId(review.getShopId());
        vo.setItemType(review.getItemType());
        vo.setItemId(review.getItemId());
        vo.setScore(review.getScore());
        vo.setContent(review.getContent());
        vo.setImages(images);
        vo.setReply(review.getReply());
        vo.setReplyTime(review.getReplyTime());
        vo.setLikeCount(review.getLikeCount());
        vo.setCreateTime(review.getCreateTime());
        return vo;
    }
}
