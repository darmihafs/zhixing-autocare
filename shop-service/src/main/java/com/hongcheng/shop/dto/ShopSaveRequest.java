package com.hongcheng.shop.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店新增/修改请求。
 */
@Data
public class ShopSaveRequest {

    @NotBlank(message = "门店名称不能为空")
    @Size(max = 64, message = "门店名称最长 64 字符")
    private String shopName;

    @Size(max = 255)
    private String logo;

    @Size(max = 20)
    private String contactPhone;

    @Size(max = 32)
    private String province;

    @NotBlank(message = "城市不能为空")
    @Size(max = 32)
    private String city;

    @Size(max = 32)
    private String district;

    @Size(max = 255)
    private String address;

    @DecimalMin(value = "-180.0", message = "经度范围 -180~180")
    @DecimalMax(value = "180.0", message = "经度范围 -180~180")
    private BigDecimal longitude;

    @DecimalMin(value = "-90.0", message = "纬度范围 -90~90")
    @DecimalMax(value = "90.0", message = "纬度范围 -90~90")
    private BigDecimal latitude;

    @Size(max = 32)
    private String businessHours;

    @Size(max = 500, message = "简介最长 500 字符")
    private String description;
}