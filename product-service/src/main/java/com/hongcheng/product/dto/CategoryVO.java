package com.hongcheng.product.dto;

import lombok.Data;

import java.util.List;

/**
 * 分类树节点。
 */
@Data
public class CategoryVO {

    private Long id;

    private String categoryName;

    private Long parentId;

    private Integer sortOrder;

    private List<CategoryVO> children;
}