package com.hongcheng.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家回复请求。
 */
@Data
public class ReplyReviewRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复不能超过500字")
    private String reply;
}
