package com.hongcheng.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类表 t_category（无逻辑删除列）。
 */
@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String categoryName;

    /** 父分类ID，0为顶级 */
    private Long parentId;

    private Integer sortOrder;

    private LocalDateTime createTime;
}