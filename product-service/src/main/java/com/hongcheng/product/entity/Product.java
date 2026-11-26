package com.hongcheng.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表 t_product。
 * <p>
 * 库存扣减不走「先查后改」，由 ProductMapper 的单条原子 UPDATE 完成。
 */
@Data
@TableName("t_product")
public class Product {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long categoryId;

    private String productName;

    private String brand;

    private String coverImg;

    /** 轮播图URL，JSON数组字符串 */
    private String images;

    private String description;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    /** 1上架 0下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}