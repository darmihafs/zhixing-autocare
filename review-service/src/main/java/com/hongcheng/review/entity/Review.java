package com.hongcheng.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务/商品评价（t_review）。
 * <p>
 * uk_order_item(order_id, item_id) 保证同一订单同一商品只能评价一次；
 * images 以 JSON 数组字符串存储，like_count 由点赞事件异步同步（后续步骤接入）。
 */
@Data
@TableName("t_review")
public class Review {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long shopId;

    /** 1商品 2服务项目 */
    private Integer itemType;

    private Long itemId;

    /** 1~5 星 */
    private Integer score;

    private String content;

    /** 晒图 URL JSON 数组 */
    private String images;

    private String reply;

    private LocalDateTime replyTime;

    private Integer likeCount;

    /** 1显示 0隐藏 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
