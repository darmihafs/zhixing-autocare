package com.hongcheng.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品服务返回的最小字段集（Feign 反序列化忽略多余字段）。
 */
@Data
public class ProductInfo {

    private Long id;

    private String productName;

    private String coverImg;

    private BigDecimal price;

    private Integer status;
}
