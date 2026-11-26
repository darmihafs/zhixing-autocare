package com.hongcheng.shop.dto;

import com.hongcheng.shop.entity.ServiceItem;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 养车服务项目详情 VO。同时作为 ES 索引 hc_service_item 的文档结构。
 * shopName 为冗余字段（写索引时从门店表带出，便于检索结果直接展示）。
 */
@Data
public class ServiceItemVO {

    private Long id;
    private Long shopId;
    private String shopName;
    private String itemName;
    private String category;
    private String coverImg;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer durationMin;
    private Integer sales;
    private Integer status;

    public static ServiceItemVO from(ServiceItem item, String shopName) {
        ServiceItemVO vo = new ServiceItemVO();
        vo.setId(item.getId());
        vo.setShopId(item.getShopId());
        vo.setShopName(shopName);
        vo.setItemName(item.getItemName());
        vo.setCategory(item.getCategory());
        vo.setCoverImg(item.getCoverImg());
        vo.setDescription(item.getDescription());
        vo.setPrice(item.getPrice());
        vo.setOriginalPrice(item.getOriginalPrice());
        vo.setDurationMin(item.getDurationMin());
        vo.setSales(item.getSales());
        vo.setStatus(item.getStatus());
        return vo;
    }
}