package com.hongcheng.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品视图（images 由 JSON 字符串解析为列表）。
 */
@Data
public class ProductVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String productName;

    private String brand;

    private String coverImg;

    private List<String> images;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    private Integer status;

    private LocalDateTime createTime;
}