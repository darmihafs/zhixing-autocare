package com.hongcheng.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.shop.dto.SearchPageVO;
import com.hongcheng.shop.dto.ServiceItemVO;
import com.hongcheng.shop.entity.ServiceItem;
import com.hongcheng.shop.entity.Shop;
import com.hongcheng.shop.mapper.ServiceItemMapper;
import com.hongcheng.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 养车服务项目查询服务（与门店详情同一套缓存三防策略）。
 */
@Service
@RequiredArgsConstructor
public class ServiceItemService {

    private final ServiceItemMapper serviceItemMapper;
    private final ShopMapper shopMapper;
    private final ShopCacheService shopCacheService;

    /** 走缓存的详情 */
    public ServiceItemVO detailCached(Long id) {
        ServiceItemVO vo = shopCacheService.getOrLoad(ShopCacheService.itemKey(id), ServiceItemVO.class,
                () -> loadItemVO(id));
        if (vo == null) {
            throw new BusinessException(ResultCode.SERVICE_ITEM_NOT_FOUND);
        }
        return vo;
    }

    /** 直连 MySQL 的详情（压测对照） */
    public ServiceItemVO detailFromDb(Long id) {
        ServiceItemVO vo = loadItemVO(id);
        if (vo == null) {
            throw new BusinessException(ResultCode.SERVICE_ITEM_NOT_FOUND);
        }
        return vo;
    }

    /** 服务间下单校验：直连 DB 获取最新上下架状态，已下架不可预约 */
    public ServiceItemVO detailForOrder(Long id) {
        ServiceItemVO vo = detailFromDb(id);
        if (!Integer.valueOf(1).equals(vo.getStatus())) {
            throw new BusinessException(ResultCode.SERVICE_ITEM_NOT_FOUND);
        }
        return vo;
    }

    /** 服务项目分页列表（仅上架，可按门店/品类过滤） */
    public SearchPageVO<ServiceItemVO> page(Long shopId, String category, long pageNum, long pageSize) {
        LambdaQueryWrapper<ServiceItem> wrapper = new LambdaQueryWrapper<ServiceItem>()
                .eq(ServiceItem::getStatus, 1)
                .eq(shopId != null, ServiceItem::getShopId, shopId)
                .eq(StringUtils.hasText(category), ServiceItem::getCategory, category)
                .orderByDesc(ServiceItem::getSales);
        Page<ServiceItem> page = serviceItemMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ServiceItemVO> records = page.getRecords().stream()
                .map(item -> ServiceItemVO.from(item, shopNameOf(item.getShopId())))
                .toList();
        return new SearchPageVO<>(page.getTotal(), pageNum, pageSize, records);
    }

    private ServiceItemVO loadItemVO(Long id) {
        ServiceItem item = serviceItemMapper.selectById(id);
        if (item == null) {
            return null;
        }
        return ServiceItemVO.from(item, shopNameOf(item.getShopId()));
    }

    private String shopNameOf(Long shopId) {
        if (shopId == null) {
            return null;
        }
        Shop shop = shopMapper.selectById(shopId);
        return shop == null ? null : shop.getShopName();
    }
}