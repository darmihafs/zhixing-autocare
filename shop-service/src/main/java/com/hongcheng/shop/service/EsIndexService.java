package com.hongcheng.shop.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import com.hongcheng.shop.dto.ServiceItemVO;
import com.hongcheng.shop.dto.ShopVO;
import com.hongcheng.shop.entity.ServiceItem;
import com.hongcheng.shop.entity.Shop;
import com.hongcheng.shop.mapper.ServiceItemMapper;
import com.hongcheng.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ES 索引维护：建索引（IK 分词 mapping）、全量灌数据、单条增量写入、重建。
 * <p>
 * 启动时索引不存在则自动创建并全量同步；已存在则跳过（后续变更由 MQ 消费者增量同步）。
 * 全量重建通过 /internal/shop/search/reindex 触发（运维/验收用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsIndexService implements ApplicationRunner {

    public static final String INDEX_SHOP = "hc_shop";
    public static final String INDEX_SERVICE_ITEM = "hc_service_item";

    /**
     * 门店索引：名称/简介/地址 ik_max_word 建索引、ik_smart 查询分词；
     * 城市/区县 keyword 用于精确过滤；评分/销量用于默认排序。
     */
    private static final String SHOP_INDEX_JSON = """
            {
              "settings": {"number_of_shards": 1, "number_of_replicas": 0},
              "mappings": {
                "properties": {
                  "id":          {"type": "long"},
                  "shopName":    {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                  "logo":        {"type": "keyword", "index": false},
                  "contactPhone":{"type": "keyword", "index": false},
                  "province":    {"type": "keyword"},
                  "city":        {"type": "keyword"},
                  "district":    {"type": "keyword"},
                  "address":     {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                  "longitude":   {"type": "double"},
                  "latitude":    {"type": "double"},
                  "businessHours":{"type": "keyword", "index": false},
                  "description": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                  "score":       {"type": "double"},
                  "sales":       {"type": "integer"},
                  "status":      {"type": "integer"}
                }
              }
            }
            """;

    /**
     * 服务项目索引：服务名/描述 IK 分词；品类 keyword 过滤；shopName 冗余展示。
     */
    private static final String SERVICE_ITEM_INDEX_JSON = """
            {
              "settings": {"number_of_shards": 1, "number_of_replicas": 0},
              "mappings": {
                "properties": {
                  "id":          {"type": "long"},
                  "shopId":      {"type": "long"},
                  "shopName":    {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                  "itemName":    {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                  "category":    {"type": "keyword"},
                  "coverImg":    {"type": "keyword", "index": false},
                  "description": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
                  "price":       {"type": "double"},
                  "originalPrice":{"type": "double"},
                  "durationMin": {"type": "integer"},
                  "sales":       {"type": "integer"},
                  "status":      {"type": "integer"}
                }
              }
            }
            """;

    private final ElasticsearchClient client;
    private final ShopMapper shopMapper;
    private final ServiceItemMapper serviceItemMapper;

    @Override
    public void run(ApplicationArguments args) {
        // ES 不可用时不阻塞服务启动，仅告警（搜索能力降级，缓存/管理接口不受影响）
        try {
            ensureIndex(INDEX_SHOP, SHOP_INDEX_JSON, this::bulkIndexAllShops);
            ensureIndex(INDEX_SERVICE_ITEM, SERVICE_ITEM_INDEX_JSON, this::bulkIndexAllItems);
        } catch (Exception e) {
            log.error("ES 索引初始化失败，搜索能力暂不可用", e);
        }
    }

    /** 全量重建两个索引（删索引 -> 建索引 -> 灌 DB 全量） */
    public void reindexAll() {
        try {
            deleteIfExists(INDEX_SHOP);
            createIndex(INDEX_SHOP, SHOP_INDEX_JSON);
            bulkIndexAllShops();
            deleteIfExists(INDEX_SERVICE_ITEM);
            createIndex(INDEX_SERVICE_ITEM, SERVICE_ITEM_INDEX_JSON);
            bulkIndexAllItems();
            log.info("ES 全量重建完成: {} + {}", INDEX_SHOP, INDEX_SERVICE_ITEM);
        } catch (IOException e) {
            throw new IllegalStateException("ES 全量重建失败", e);
        }
    }

    public void indexShop(Long id) {
        Shop shop = shopMapper.selectById(id);
        try {
            if (shop == null) {
                deleteDoc(INDEX_SHOP, id);
                return;
            }
            client.index(i -> i.index(INDEX_SHOP).id(String.valueOf(id)).document(ShopVO.from(shop)));
            log.info("ES 门店索引已更新 id={}", id);
        } catch (IOException e) {
            throw new IllegalStateException("ES 门店索引更新失败 id=" + id, e);
        }
    }

    public void indexServiceItem(Long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        try {
            if (item == null) {
                deleteDoc(INDEX_SERVICE_ITEM, id);
                return;
            }
            client.index(i -> i.index(INDEX_SERVICE_ITEM).id(String.valueOf(id)).document(toItemVO(item)));
            log.info("ES 服务项目索引已更新 id={}", id);
        } catch (IOException e) {
            throw new IllegalStateException("ES 服务项目索引更新失败 id=" + id, e);
        }
    }

    private void ensureIndex(String index, String bodyJson,
                             java.util.concurrent.Callable<Void> bulkLoader) throws Exception {
        if (client.indices().exists(e -> e.index(index)).value()) {
            log.info("ES 索引已存在，跳过全量同步: {}", index);
            return;
        }
        createIndex(index, bodyJson);
        bulkLoader.call();
        log.info("ES 索引已创建并完成全量同步: {}", index);
    }

    private void createIndex(String index, String bodyJson) throws IOException {
        CreateIndexRequest.Builder builder = new CreateIndexRequest.Builder().index(index);
        builder.withJson(new StringReader(bodyJson));
        client.indices().create(builder.build());
    }

    private void deleteIfExists(String index) throws IOException {
        if (client.indices().exists(e -> e.index(index)).value()) {
            client.indices().delete(DeleteIndexRequest.of(d -> d.index(index)));
        }
    }

    private void deleteDoc(String index, Long id) throws IOException {
        client.delete(d -> d.index(index).id(String.valueOf(id)));
        log.info("ES 索引已删除 id={}, index={}", id, index);
    }

    private Void bulkIndexAllShops() throws IOException {
        List<Shop> shops = shopMapper.selectList(null);
        if (shops.isEmpty()) {
            return null;
        }
        BulkRequest.Builder bulk = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        for (Shop shop : shops) {
            ShopVO vo = ShopVO.from(shop);
            bulk.operations(op -> op.index(idx -> idx.index(INDEX_SHOP)
                    .id(String.valueOf(vo.getId())).document(vo)));
        }
        client.bulk(bulk.build());
        log.info("ES 门店全量索引 {} 条", shops.size());
        return null;
    }

    private Void bulkIndexAllItems() throws IOException {
        List<ServiceItem> items = serviceItemMapper.selectList(null);
        if (items.isEmpty()) {
            return null;
        }
        Map<Long, String> shopNames = shopNameMap();
        BulkRequest.Builder bulk = new BulkRequest.Builder().refresh(Refresh.WaitFor);
        for (ServiceItem item : items) {
            ServiceItemVO vo = ServiceItemVO.from(item, shopNames.get(item.getShopId()));
            bulk.operations(op -> op.index(idx -> idx.index(INDEX_SERVICE_ITEM)
                    .id(String.valueOf(vo.getId())).document(vo)));
        }
        client.bulk(bulk.build());
        log.info("ES 服务项目全量索引 {} 条", items.size());
        return null;
    }

    private ServiceItemVO toItemVO(ServiceItem item) {
        Shop shop = item.getShopId() == null ? null : shopMapper.selectById(item.getShopId());
        return ServiceItemVO.from(item, shop == null ? null : shop.getShopName());
    }

    private Map<Long, String> shopNameMap() {
        return shopMapper.selectList(null).stream()
                .collect(Collectors.toMap(Shop::getId, Shop::getShopName, (a, b) -> a));
    }
}