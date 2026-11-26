package com.hongcheng.shop.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.hongcheng.shop.dto.SearchPageVO;
import com.hongcheng.shop.dto.ServiceItemSearchVO;
import com.hongcheng.shop.dto.ServiceItemVO;
import com.hongcheng.shop.dto.ShopSearchVO;
import com.hongcheng.shop.dto.ShopVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * ES 检索：中文 IK 分词（mapping 中 ik_max_word 建索引 / ik_smart 查询）、
 * 多字段相关度打分（名称权重最高）、命中高亮、status 等精确过滤。
 * 关键字为空时退化为“浏览模式”：按销量倒序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final String HIGHLIGHT_PRE_TAG = "<em>";
    private static final String HIGHLIGHT_POST_TAG = "</em>";
    private static final long MAX_PAGE_NUM = 100L;
    private static final long MAX_PAGE_SIZE = 50L;

    private final ElasticsearchClient client;

    /** 门店检索：名称^3 + 简介 + 地址，可按城市过滤，按相关度排序 */
    public SearchPageVO<ShopSearchVO> searchShops(String keyword, String city, long pageNum, long pageSize) {
        long safePage = clampPageNum(pageNum);
        long safeSize = clampPageSize(pageSize);
        BoolQuery keywordQuery = buildBool(keyword, "shopName^3", "description", "address");
        BoolQuery bool = keywordQuery;
        if (StringUtils.hasText(city)) {
            bool = BoolQuery.of(b -> b.must(m -> m.bool(keywordQuery))
                    .filter(f -> f.term(t -> t.field("city").value(city))));
        }
        SearchRequest.Builder builder = baseRequest(EsIndexService.INDEX_SHOP, keyword, safePage, safeSize, bool);
        builder.highlight(h -> h.preTags(HIGHLIGHT_PRE_TAG).postTags(HIGHLIGHT_POST_TAG)
                .fields("shopName", f -> f)
                .fields("description", f -> f));
        try {
            SearchResponse<ShopVO> response = client.search(builder.build(), ShopVO.class);
            List<ShopSearchVO> records = response.hits().hits().stream().map(hit -> {
                ShopSearchVO vo = new ShopSearchVO();
                BeanUtils.copyProperties(hit.source(), vo);
                fillHighlight(hit, vo::setHighlightName, vo::setHighlightDescription);
                return vo;
            }).toList();
            return new SearchPageVO<>(totalOf(response), safePage, safeSize, records);
        } catch (IOException e) {
            throw new IllegalStateException("门店检索失败: " + keyword, e);
        }
    }

    /** 服务项目检索：名称^3 + 描述，可按品类/门店过滤，按相关度排序 */
    public SearchPageVO<ServiceItemSearchVO> searchServiceItems(String keyword, String category, Long shopId,
                                                                long pageNum, long pageSize) {
        long safePage = clampPageNum(pageNum);
        long safeSize = clampPageSize(pageSize);
        BoolQuery bool = buildBool(keyword, "itemName^3", "description");
        BoolQuery.Builder extra = new BoolQuery.Builder().must(m -> m.bool(bool));
        if (StringUtils.hasText(category)) {
            extra.filter(f -> f.term(t -> t.field("category").value(category)));
        }
        if (shopId != null) {
            extra.filter(f -> f.term(t -> t.field("shopId").value(shopId)));
        }
        BoolQuery finalQuery = extra.build();
        SearchRequest.Builder builder = baseRequest(EsIndexService.INDEX_SERVICE_ITEM, keyword,
                safePage, safeSize, finalQuery);
        builder.highlight(h -> h.preTags(HIGHLIGHT_PRE_TAG).postTags(HIGHLIGHT_POST_TAG)
                .fields("itemName", f -> f)
                .fields("description", f -> f));
        try {
            SearchResponse<ServiceItemVO> response = client.search(builder.build(), ServiceItemVO.class);
            List<ServiceItemSearchVO> records = response.hits().hits().stream().map(hit -> {
                ServiceItemSearchVO vo = new ServiceItemSearchVO();
                BeanUtils.copyProperties(hit.source(), vo);
                fillHighlight(hit, vo::setHighlightName, vo::setHighlightDescription);
                return vo;
            }).toList();
            return new SearchPageVO<>(totalOf(response), safePage, safeSize, records);
        } catch (IOException e) {
            throw new IllegalStateException("服务项目检索失败: " + keyword, e);
        }
    }

    /**
     * 关键词多字段检索 + 仅营业/上架过滤。
     */
    private BoolQuery buildBool(String keyword, String nameField, String... otherFields) {
        BoolQuery.Builder builder = new BoolQuery.Builder();
        if (StringUtils.hasText(keyword)) {
            List<String> fields = new java.util.ArrayList<>();
            fields.add(nameField);
            fields.addAll(List.of(otherFields));
            builder.must(m -> m.multiMatch(mm -> mm.query(keyword).fields(fields)));
        } else {
            builder.must(m -> m.matchAll(ma -> ma));
        }
        builder.filter(f -> f.term(t -> t.field("status").value(1)));
        return builder.build();
    }

    private SearchRequest.Builder baseRequest(String index, String keyword, long pageNum, long pageSize,
                                              BoolQuery bool) {
        long from = Math.max(0L, (pageNum - 1) * pageSize);
        SearchRequest.Builder builder = new SearchRequest.Builder()
                .index(index)
                .from((int) from)
                .size((int) pageSize)
                .query(q -> q.bool(bool));
        if (StringUtils.hasText(keyword)) {
            // 相关度优先（_score 倒序）
            builder.sort(s -> s.score(so -> so.order(SortOrder.Desc)));
        } else {
            // 浏览模式按销量倒序
            builder.sort(s -> s.field(f -> f.field("sales").order(SortOrder.Desc)));
        }
        return builder;
    }

    private long clampPageNum(long pageNum) {
        return Math.min(Math.max(1L, pageNum), MAX_PAGE_NUM);
    }

    private long clampPageSize(long pageSize) {
        return Math.min(Math.max(1L, pageSize), MAX_PAGE_SIZE);
    }

    private long totalOf(SearchResponse<?> response) {
        return response.hits().total() == null ? 0 : response.hits().total().value();
    }

    private void fillHighlight(Hit<?> hit, java.util.function.Consumer<String> nameSetter,
                               java.util.function.Consumer<String> descriptionSetter) {
        Map<String, List<String>> highlight = hit.highlight();
        if (highlight == null) {
            return;
        }
        nameSetter.accept(firstFragment(hit, highlight, "shopName", "itemName"));
        descriptionSetter.accept(firstFragment(hit, highlight, "description"));
    }

    private String firstFragment(Hit<?> hit, Map<String, List<String>> highlight, String... fieldNames) {
        for (String field : fieldNames) {
            List<String> fragments = highlight.get(field);
            if (fragments != null && !fragments.isEmpty()) {
                return fragments.get(0);
            }
        }
        return null;
    }
}