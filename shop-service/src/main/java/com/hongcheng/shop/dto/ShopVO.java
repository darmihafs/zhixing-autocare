package com.hongcheng.shop.dto;

import com.hongcheng.shop.entity.Shop;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店详情 VO。同时作为 ES 索引 hc_shop 的文档结构（字段名与 mapping 一致）。
 */
@Data
public class ShopVO {

    private Long id;
    private String shopName;
    private String logo;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String businessHours;
    private String description;
    private BigDecimal score;
    private Integer sales;
    private Integer status;

    public static ShopVO from(Shop shop) {
        ShopVO vo = new ShopVO();
        vo.setId(shop.getId());
        vo.setShopName(shop.getShopName());
        vo.setLogo(shop.getLogo());
        vo.setContactPhone(shop.getContactPhone());
        vo.setProvince(shop.getProvince());
        vo.setCity(shop.getCity());
        vo.setDistrict(shop.getDistrict());
        vo.setAddress(shop.getAddress());
        vo.setLongitude(shop.getLongitude());
        vo.setLatitude(shop.getLatitude());
        vo.setBusinessHours(shop.getBusinessHours());
        vo.setDescription(shop.getDescription());
        vo.setScore(shop.getScore());
        vo.setSales(shop.getSales());
        vo.setStatus(shop.getStatus());
        return vo;
    }
}