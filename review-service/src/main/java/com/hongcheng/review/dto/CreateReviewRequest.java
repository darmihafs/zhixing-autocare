package com.hongcheng.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建评价请求（订单归属、完成状态、明细归属由订单服务内部接口校验）。
 */
@Data
public class CreateReviewRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    @NotNull(message = "评价商品不能为空")
    @Min(value = 1, message = "商品ID非法")
    private Long itemId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    private Integer score;

    @Size(max = 1000, message = "评价内容不能超过1000字")
    private String content;

    @Size(max = 5, message = "最多上传5张图片")
    private List<@Size(max = 180, message = "图片URL过长") String> images;
}
