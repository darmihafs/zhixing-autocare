package com.hongcheng.review.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 评价晒图上传结果：返回可由网关直接访问的相对地址。
 */
@Data
@AllArgsConstructor
public class ReviewImageVO {

    /** 浏览器可访问的相对地址，如 /api/review/uploads/20261116/xxx.jpg */
    private String url;

    /** 原始文件名 */
    private String name;

    /** 文件字节数 */
    private long size;
}
