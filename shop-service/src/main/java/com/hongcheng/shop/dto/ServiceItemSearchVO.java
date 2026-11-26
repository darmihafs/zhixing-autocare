package com.hongcheng.shop.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务项目 ES 检索结果：原始字段 + 高亮片段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceItemSearchVO extends ServiceItemVO {

    /** 服务名称高亮片段 */
    private String highlightName;

    /** 描述高亮片段 */
    private String highlightDescription;
}