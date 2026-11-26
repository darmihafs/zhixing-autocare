package com.hongcheng.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.ai.dto.ToolCall;
import com.hongcheng.ai.dto.ToolResult;
import com.hongcheng.ai.feign.OrderClient;
import com.hongcheng.ai.feign.ProductClient;
import com.hongcheng.ai.feign.ShopClient;
import com.hongcheng.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 工具执行器单测：未登录查订单拦截（绝不越权）、真实商品/订单卡片解析、dispatch 路由、下游失败容错。
 */
@ExtendWith(MockitoExtension.class)
class ToolExecutorTest {

    @Mock
    private ProductClient productClient;
    @Mock
    private ShopClient shopClient;
    @Mock
    private OrderClient orderClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor(productClient, shopClient, orderClient, mapper);
    }

    private JsonNode page(String recordsJson) throws Exception {
        return mapper.readTree("{\"records\":" + recordsJson + ",\"total\":1}");
    }

    @Test
    @DisplayName("未登录查订单 - 明确提示需登录且绝不调用订单接口")
    void queryMyOrders_whenAnonymous_interceptsAndNeverCallsFeign() {
        ToolResult r = executor.queryMyOrders(null);
        assertThat(r.getText()).contains("登录");
        assertThat(r.getCards().path("orders").size()).isZero();
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("dispatch query_my_orders 未登录 - 同样拦截且零 Feign 调用")
    void dispatch_queryOrdersAnonymous_intercepts() {
        ToolResult r = executor.dispatch(new ToolCall("1", "query_my_orders", "{}"), null);
        assertThat(r.getText()).contains("登录");
        verifyNoInteractions(orderClient);
    }

    @Test
    @DisplayName("已登录查订单 - 透传 userId 并解析真实订单卡片")
    void queryMyOrders_whenLoggedIn_returnsRealCards() throws Exception {
        when(orderClient.page(99L, 1, 5)).thenReturn(Result.ok(page("[{\"id\":1,\"orderNo\":\"HC123\",\"status\":10}]")));
        ToolResult r = executor.queryMyOrders(99L);
        assertThat(r.getCards().path("orders").size()).isEqualTo(1);
        assertThat(r.getCards().path("orders").get(0).path("orderNo").asText()).isEqualTo("HC123");
        verify(orderClient).page(99L, 1, 5);
    }

    @Test
    @DisplayName("search_products - 解析真实在售商品卡片与文本摘要")
    void searchProducts_parsesRealRecords() throws Exception {
        when(productClient.page("机油", 1, 6))
                .thenReturn(Result.ok(page("[{\"id\":7,\"productName\":\"全合成机油\",\"price\":199.00,\"stock\":50}]")));
        ToolResult r = executor.searchProducts("机油");
        assertThat(r.getCards().path("products").size()).isEqualTo(1);
        assertThat(r.getCards().path("products").get(0).path("productName").asText()).isEqualTo("全合成机油");
        assertThat(r.getText()).contains("全合成机油");
    }

    @Test
    @DisplayName("dispatch search_products - 按工具名路由到商品客户端，不碰订单/门店")
    void dispatch_searchProducts_routesToProductClient() throws Exception {
        when(productClient.page("机油", 1, 6)).thenReturn(Result.ok(page("[{\"id\":7,\"productName\":\"机油\",\"price\":1}]")));
        ToolResult r = executor.dispatch(new ToolCall("1", "search_products", "{\"keyword\":\"机油\"}"), null);
        assertThat(r.getCards().path("products").size()).isEqualTo(1);
        verify(productClient).page("机油", 1, 6);
        verifyNoInteractions(orderClient, shopClient);
    }

    @Test
    @DisplayName("search_products 下游 503 - 友好提示且不抛异常")
    void searchProducts_whenDownstreamFails_returnsFriendlyText() {
        when(productClient.page("机油", 1, 6)).thenReturn(Result.fail(503, "商品服务繁忙"));
        ToolResult r = executor.searchProducts("机油");
        assertThat(r.getText()).contains("暂不可用");
        assertThat(r.getCards().path("products").size()).isZero();
    }

    @Test
    @DisplayName("parseArgs 非法 JSON - 容错为空对象不抛异常")
    void parseArgs_whenInvalidJson_returnsEmptyObject() {
        JsonNode n = executor.parseArgs("not-json");
        assertThat(n.isObject()).isTrue();
        assertThat(n.path("keyword").asText("")).isEmpty();
    }
}