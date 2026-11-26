package com.hongcheng.shop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索/分页统一返回结构（ES 搜索与 MyBatis-Plus 分页共用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchPageVO<T> {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> records;
}