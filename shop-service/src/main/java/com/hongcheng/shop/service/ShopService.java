package com.hongcheng.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.shop.dto.SearchPageVO;
import com.hongcheng.shop.dto.ShopVO;
import com.hongcheng.shop.entity.Shop;
import com.hongcheng.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 门店查询服务：
 * - {@link #detailCached(Long)} 走 Redis 缓存三防（简历对照接口）；
 * - {@link #detailFromDb(Long)} 直连 MySQL（压测对照组）；
 * - {@link #page} MyBatis-Plus 物理分页。
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopMapper shopMapper;
    private final ShopCacheService shopCacheService;

    /** 走缓存的详情（防穿透/击穿/雪崩） */
    public ShopVO detailCached(Long id) {
        ShopVO vo = shopCacheService.getOrLoad(ShopCacheService.shopKey(id), ShopVO.class,
                () -> loadShopVO(id));
        if (vo == null) {
            throw new BusinessException(ResultCode.SHOP_NOT_FOUND);
        }
        return vo;
    }

    /** 直连 MySQL 的详情（压测对照，永不读写缓存） */
    public ShopVO detailFromDb(Long id) {
        ShopVO vo = loadShopVO(id);
        if (vo == null) {
            throw new BusinessException(ResultCode.SHOP_NOT_FOUND);
        }
        return vo;
    }

    /** 门店分页列表（仅营业中，可按城市过滤、门招牌模糊搜索） */
    public SearchPageVO<ShopVO> page(String city, String keyword, long pageNum, long pageSize) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, 1)
                .eq(StringUtils.hasText(city), Shop::getCity, city)
                .like(StringUtils.hasText(keyword), Shop::getShopName, keyword)
                .orderByDesc(Shop::getSales);
        Page<Shop> page = shopMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ShopVO> records = page.getRecords().stream().map(ShopVO::from).toList();
        return new SearchPageVO<>(page.getTotal(), pageNum, pageSize, records);
    }

    private ShopVO loadShopVO(Long id) {
        Shop shop = shopMapper.selectById(id);
        return shop == null ? null : ShopVO.from(shop);
    }
}