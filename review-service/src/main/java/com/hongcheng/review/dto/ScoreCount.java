package com.hongcheng.review.dto;

import lombok.Data;

/**
 * 星级分布统计行（Mapper 聚合结果）。
 */
@Data
public class ScoreCount {

    private Integer score;

    private Long cnt;
}
