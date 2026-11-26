package com.hongcheng.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 养车服务项目表 t_service_item。
 */
@Data
@TableName("t_service_item")
public class ServiceItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long shopId;

    private String itemName;

    /** 保养/洗车/轮胎/美容/检测/救援 */
    private String category;

    private String coverImg;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer durationMin;

    private Integer sales;

    /** 1上架 0下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}