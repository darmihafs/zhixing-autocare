package com.hongcheng.ai.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongcheng.ai.dto.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 降级兜底：未配 Key / 大模型超时 / 余额不足 / 被限流熔断时，
 * 提取关键词直接调用真实检索工具，返回真实推荐卡片 + 友好提示，保证接口永远可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendFallbackService {

    /** 领域词优先命中，保证降级检索能落到真实商品/服务 */
    private static final List<String> DOMAIN_TERMS = List.of(
            "机油", "洗车", "轮胎", "空调", "保养", "滤芯", "雨刮", "检测", "美容", "刹车", "电瓶");

    private final ToolExecutor toolExecutor;

    public ToolResult build(String message, Long userId) {
        String keyword = extractKeyword(message);
        ToolResult products = toolExecutor.searchProducts(keyword);
        ToolResult shops = toolExecutor.searchShopsAndServices(keyword);
        ObjectNode cards = toolExecutor.emptyCards();
        toolExecutor.mergeCards(cards, products.getCards());
        toolExecutor.mergeCards(cards, shops.getCards());

        StringBuilder text = new StringBuilder();
        text.append("已为你检索到真实结果：商品 ")
                .append(cards.path("products").size()).append(" 件、门店 ")
                .append(cards.path("shops").size()).append(" 家、服务项目 ")
                .append(cards.path("services").size()).append(" 项，点击卡片即可查看详情。");

        if (message != null && message.contains("订单")) {
            if (userId == null) {
                text.append(" 查询订单需要先登录，登录后我才能帮你看订单状态。");
            } else {
                ToolResult orders = toolExecutor.queryMyOrders(userId);
                toolExecutor.mergeCards(cards, orders.getCards());
                text.append(" ").append(orders.getText());
            }
        }
        return new ToolResult(text.toString(), cards);
    }

    /** 优先取领域词；否则去掉口语停用词与标点后的剩余文本 */
    public String extractKeyword(String message) {
        String msg = message == null ? "" : message.trim();
        for (String term : DOMAIN_TERMS) {
            if (msg.contains(term)) return term;
        }
        String cleaned = msg.replaceAll("[？?！!。，,、；;：:“”\"'《》（）()\\s]", "");
        for (String stop : List.of("请问", "帮我", "我想", "推荐", "一下", "附近", "哪里", "可以", "查", "我的", "吗", "呢")) {
            cleaned = cleaned.replace(stop, "");
        }
        return cleaned.isEmpty() ? msg : cleaned;
    }
}