package com.hongcheng.review.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 评价聚合统计：平均分 + 总条数 + 1~5 星分布。
 */
@Data
public class ReviewStatsVO {

    private Integer itemType;

    private Long itemId;

    private long total;

    private BigDecimal avgScore;

    /** key=星级 1~5，value=条数（无数据的星级为 0） */
    private Map<Integer, Long> distribution;
}
