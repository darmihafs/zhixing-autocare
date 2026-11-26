package com.hongcheng.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongcheng.ai.dto.ToolCall;
import com.hongcheng.ai.dto.ToolResult;
import com.hongcheng.ai.feign.OrderClient;
import com.hongcheng.ai.feign.ProductClient;
import com.hongcheng.ai.feign.ShopClient;
import com.hongcheng.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 真实工具执行器：全部经 OpenFeign + X-Internal-Token 调用现有微服务，
 * 不造假数据。query_my_orders 严格依赖服务端注入的 X-User-Id，未登录直接拦截。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutor {

    public static final String TOOL_SEARCH_PRODUCTS = "search_products";
    public static final String TOOL_SEARCH_SHOPS = "search_shops_and_services";
    public static final String TOOL_QUERY_ORDERS = "query_my_orders";

    private final ProductClient productClient;
    private final ShopClient shopClient;
    private final OrderClient orderClient;
    private final ObjectMapper mapper;

    /** 按工具名分发（ReAct 循环与单元测试共用入口） */
    public ToolResult dispatch(ToolCall call, Long userId) {
        JsonNode args = parseArgs(call.getArguments());
        String keyword = args.path("keyword").asText("").trim();
        return switch (call.getName() == null ? "" : call.getName()) {
            case TOOL_SEARCH_PRODUCTS -> searchProducts(keyword);
            case TOOL_SEARCH_SHOPS -> searchShopsAndServices(keyword);
            case TOOL_QUERY_ORDERS -> queryMyOrders(userId);
            default -> new ToolResult("未知工具: " + call.getName(), emptyCards());
        };
    }

    public JsonNode parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) return mapper.createObjectNode();
        try {
            return mapper.readTree(arguments);
        } catch (Exception e) {
            log.warn("工具 arguments 解析失败: {}", e.getMessage());
            return mapper.createObjectNode();
        }
    }

    public ToolResult searchProducts(String keyword) {
        ObjectNode cards = emptyCards();
        Result<JsonNode> r = productClient.page(keyword, 1, 6);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            return new ToolResult("商品检索暂不可用，请稍后重试。", cards);
        }
        ArrayNode records = (ArrayNode) r.getData().path("records");
        ArrayNode products = cards.putArray("products");
        StringBuilder sb = new StringBuilder("找到 " + records.size() + " 件相关在售商品：");
        for (JsonNode p : records) {
            products.add(p);
            sb.append(p.path("productName").asText()).append("(¥").append(p.path("price").asText()).append(") ");
        }
        return new ToolResult(sb.toString().trim(), cards);
    }

    public ToolResult searchShopsAndServices(String keyword) {
        ObjectNode cards = emptyCards();
        StringBuilder sb = new StringBuilder();
        Result<JsonNode> shopR = shopClient.searchShops(keyword, 1, 3);
        if (shopR != null && shopR.isSuccess() && shopR.getData() != null) {
            ArrayNode records = (ArrayNode) shopR.getData().path("records");
            ArrayNode shops = cards.putArray("shops");
            sb.append("门店 ").append(records.size()).append(" 家：");
            for (JsonNode s : records) {
                shops.add(s);
                sb.append(s.path("shopName").asText()).append(" ");
            }
        }
        Result<JsonNode> svcR = shopClient.searchServices(keyword, 1, 3);
        if (svcR != null && svcR.isSuccess() && svcR.getData() != null) {
            ArrayNode records = (ArrayNode) svcR.getData().path("records");
            ArrayNode services = cards.putArray("services");
            sb.append("；服务项目 ").append(records.size()).append(" 项：");
            for (JsonNode s : records) {
                services.add(s);
                sb.append(s.path("itemName").asText()).append("(¥").append(s.path("price").asText()).append(") ");
            }
        }
        if (sb.length() == 0) sb.append("门店/服务检索暂不可用，请稍后重试。");
        return new ToolResult(sb.toString().trim(), cards);
    }

    /** 未登录（userId 为 null）时明确提示需登录，绝不调用订单接口、绝不越权 */
    public ToolResult queryMyOrders(Long userId) {
        ObjectNode cards = emptyCards();
        if (userId == null) {
            return new ToolResult("查询订单需要先登录。请登录后再让我帮你查订单状态。", cards);
        }
        Result<JsonNode> r = orderClient.page(userId, 1, 5);
        if (r == null || !r.isSuccess() || r.getData() == null) {
            return new ToolResult("订单查询暂不可用，请稍后重试。", cards);
        }
        ArrayNode records = (ArrayNode) r.getData().path("records");
        ArrayNode orders = cards.putArray("orders");
        StringBuilder sb = new StringBuilder("你最近有 " + records.size() + " 笔订单：");
        for (JsonNode o : records) {
            orders.add(o);
            sb.append(o.path("orderNo").asText()).append("(状态 ").append(o.path("status").asInt()).append(") ");
        }
        return new ToolResult(sb.toString().trim(), cards);
    }

    public ObjectNode emptyCards() {
        ObjectNode cards = mapper.createObjectNode();
        cards.putArray("products");
        cards.putArray("shops");
        cards.putArray("services");
        cards.putArray("orders");
        return cards;
    }

    /** 把 src 的各卡片数组合并进 dst（去重按 id/orderNo） */
    public void mergeCards(ObjectNode dst, ObjectNode src) {
        if (src == null) return;
        for (String key : new String[]{"products", "shops", "services", "orders"}) {
            JsonNode srcArr = src.path(key);
            if (!srcArr.isArray()) continue;
            ArrayNode dstArr = dst.has(key) && dst.get(key).isArray()
                    ? (ArrayNode) dst.get(key) : dst.putArray(key);
            for (JsonNode item : srcArr) {
                if (!containsId(dstArr, item)) dstArr.add(item);
            }
        }
    }

    private boolean containsId(ArrayNode arr, JsonNode item) {
        String id = item.path("id").asText(item.path("orderNo").asText(""));
        if (id.isEmpty()) return false;
        for (JsonNode existing : arr) {
            String eid = existing.path("id").asText(existing.path("orderNo").asText(""));
            if (id.equals(eid)) return true;
        }
        return false;
    }
}