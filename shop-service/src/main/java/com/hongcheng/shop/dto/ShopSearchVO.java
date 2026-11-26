package com.hongcheng.shop.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 门店 ES 检索结果：原始字段 + 高亮片段（<em> 标签包裹命中的分词）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopSearchVO extends ShopVO {

    /** 门店名称高亮片段，如：洪兴洗车·<em>青山湖</em>旗舰店 */
    private String highlightName;

    /** 简介高亮片段 */
    private String highlightDescription;
}