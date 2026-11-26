package com.hongcheng.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 养车门店表 t_shop。
 */
@Data
@TableName("t_shop")
public class Shop {

    @TableId(type = IdType.ASSIGN_ID)
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

    /** 1营业 0停业 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}