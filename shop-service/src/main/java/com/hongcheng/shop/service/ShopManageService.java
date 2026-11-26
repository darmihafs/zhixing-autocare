package com.hongcheng.shop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.shop.dto.ServiceItemSaveRequest;
import com.hongcheng.shop.dto.ShopSaveRequest;
import com.hongcheng.shop.dto.ShopSyncMessage;
import com.hongcheng.shop.entity.ServiceItem;
import com.hongcheng.shop.entity.Shop;
import com.hongcheng.shop.mapper.ServiceItemMapper;
import com.hongcheng.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 门店/服务项目管理端写操作：
 * 1) 落库；
 * 2) 删缓存（Cache-Aside：下次读请求回源重建）；
 * 3) RocketMQ 异步发消息（HC_SHOP_SYNC），由消费者增量更新 ES 索引。
 * 业务线程不等待 ES 写入，搜索侧最终一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopManageService {

    public static final String ES_SYNC_TOPIC = "HC_SHOP_SYNC";
    public static final String TYPE_SHOP = "SHOP";
    public static final String TYPE_SERVICE_ITEM = "SERVICE_ITEM";

    private final ShopMapper shopMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final ShopCacheService shopCacheService;
    private final RocketMQTemplate rocketMQTemplate;

    /** 管理端门店分页（含下架，便于上下架管理） */
    public Page<Shop> pageShops(String keyword, Integer status, Integer pageNum, Integer pageSize) {
        return shopMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Shop>lambdaQuery()
                        .like(StringUtils.hasText(keyword), Shop::getShopName, keyword)
                        .eq(status != null, Shop::getStatus, status)
                        .orderByDesc(Shop::getId));
    }

    /** 管理端服务项目分页（可按门店/状态过滤） */
    public Page<ServiceItem> pageServiceItems(Long shopId, Integer status, Integer pageNum, Integer pageSize) {
        return serviceItemMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<ServiceItem>lambdaQuery()
                        .eq(shopId != null, ServiceItem::getShopId, shopId)
                        .eq(status != null, ServiceItem::getStatus, status)
                        .orderByDesc(ServiceItem::getId));
    }

    public Long createShop(ShopSaveRequest request) {
        Shop shop = new Shop();
        applyShop(shop, request);
        shop.setScore(new BigDecimal("5.0"));
        shop.setSales(0);
        shop.setStatus(1);
        shopMapper.insert(shop);
        log.info("门店创建成功 id={}, name={}", shop.getId(), shop.getShopName());
        sendSyncMessage(TYPE_SHOP, shop.getId());
        return shop.getId();
    }

    public void updateShop(Long id, ShopSaveRequest request) {
        Shop shop = requireShop(id);
        applyShop(shop, request);
        shopMapper.updateById(shop);
        shopCacheService.evict(ShopCacheService.shopKey(id));
        log.info("门店更新成功 id={}", id);
        sendSyncMessage(TYPE_SHOP, id);
    }

    /** 上架(1)/下架(0)：删缓存 + 同步 ES（ES 侧按 status 过滤，下架后检索不可见） */
    public void changeShopStatus(Long id, Integer status) {
        validateStatus(status);
        Shop shop = requireShop(id);
        shop.setStatus(status);
        shopMapper.updateById(shop);
        shopCacheService.evict(ShopCacheService.shopKey(id));
        log.info("门店状态变更成功 id={}, status={}", id, status);
        sendSyncMessage(TYPE_SHOP, id);
    }

    public Long createServiceItem(ServiceItemSaveRequest request) {
        requireShop(request.getShopId());
        ServiceItem item = new ServiceItem();
        applyItem(item, request);
        item.setSales(0);
        item.setStatus(1);
        serviceItemMapper.insert(item);
        log.info("服务项目创建成功 id={}, name={}", item.getId(), item.getItemName());
        sendSyncMessage(TYPE_SERVICE_ITEM, item.getId());
        return item.getId();
    }

    public void updateServiceItem(Long id, ServiceItemSaveRequest request) {
        ServiceItem item = requireServiceItem(id);
        requireShop(request.getShopId());
        applyItem(item, request);
        serviceItemMapper.updateById(item);
        shopCacheService.evict(ShopCacheService.itemKey(id));
        log.info("服务项目更新成功 id={}", id);
        sendSyncMessage(TYPE_SERVICE_ITEM, id);
    }

    public void changeServiceItemStatus(Long id, Integer status) {
        validateStatus(status);
        ServiceItem item = requireServiceItem(id);
        item.setStatus(status);
        serviceItemMapper.updateById(item);
        shopCacheService.evict(ShopCacheService.itemKey(id));
        log.info("服务项目状态变更成功 id={}, status={}", id, status);
        sendSyncMessage(TYPE_SERVICE_ITEM, id);
    }

    /**
     * 异步发送 ES 同步消息（tag = 类型）。
     * 发送失败仅记录日志：索引可通过 /internal/shop/search/reindex 全量重建兜底。
     */
    private void sendSyncMessage(String type, Long id) {
        ShopSyncMessage message = new ShopSyncMessage(type, id, System.currentTimeMillis());
        rocketMQTemplate.asyncSend(ES_SYNC_TOPIC + ":" + type, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("ES 同步消息发送成功 type={}, id={}, msgId={}", type, id, sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("ES 同步消息发送失败 type={}, id={}", type, id, e);
            }
        });
    }

    private Shop requireShop(Long id) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new BusinessException(ResultCode.SHOP_NOT_FOUND);
        }
        return shop;
    }

    private ServiceItem requireServiceItem(Long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ResultCode.SERVICE_ITEM_NOT_FOUND);
        }
        return item;
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "状态只能为 0(下架) 或 1(上架)");
        }
    }

    private void applyShop(Shop shop, ShopSaveRequest request) {
        shop.setShopName(request.getShopName());
        shop.setLogo(request.getLogo());
        shop.setContactPhone(request.getContactPhone());
        shop.setProvince(request.getProvince());
        shop.setCity(request.getCity());
        shop.setDistrict(request.getDistrict());
        shop.setAddress(request.getAddress());
        shop.setLongitude(request.getLongitude());
        shop.setLatitude(request.getLatitude());
        shop.setBusinessHours(request.getBusinessHours());
        shop.setDescription(request.getDescription());
    }

    private void applyItem(ServiceItem item, ServiceItemSaveRequest request) {
        item.setShopId(request.getShopId());
        item.setItemName(request.getItemName());
        item.setCategory(request.getCategory());
        item.setCoverImg(request.getCoverImg());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setOriginalPrice(request.getOriginalPrice());
        item.setDurationMin(request.getDurationMin());
    }
}