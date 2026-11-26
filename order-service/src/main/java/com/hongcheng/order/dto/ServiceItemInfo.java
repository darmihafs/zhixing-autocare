package com.hongcheng.order.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店服务项目返回的最小字段集（Feign 反序列化忽略多余字段）。
 */
@Data
public class ServiceItemInfo {

    private Long id;

    private Long shopId;

    private String shopName;

    private String itemName;

    private String coverImg;

    private BigDecimal price;

    private Integer status;
}
