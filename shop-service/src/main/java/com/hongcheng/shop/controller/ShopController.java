package com.hongcheng.shop.controller;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.shop.dto.SearchPageVO;
import com.hongcheng.shop.dto.ServiceItemSearchVO;
import com.hongcheng.shop.dto.ServiceItemVO;
import com.hongcheng.shop.dto.ShopSearchVO;
import com.hongcheng.shop.dto.ShopVO;
import com.hongcheng.shop.service.SearchService;
import com.hongcheng.shop.service.ServiceItemService;
import com.hongcheng.shop.service.ShopService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店/养车服务公开查询接口（匿名可访问，网关白名单）。
 * <p>
 * 详情主通道 /shop/{id} 走 Redis 缓存三防；直连库压测接口 /shop/db/{id}、/shop/service/db/{id}
 * 默认关闭（hc.perf.enabled=false），仅压测时按需放开。
 */
@Validated
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final ServiceItemService serviceItemService;
    private final SearchService searchService;

    @Value("${hc.perf.enabled:false}")
    private boolean perfEnabled = false;

    /** 门店分页（DB，含分页插件验证点） */
    @GetMapping("/page")
    public Result<SearchPageVO<ShopVO>> page(@RequestParam(defaultValue = "1") @Min(1) @Max(100) long pageNum,
                                             @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize,
                                             @RequestParam(required = false) String city,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(shopService.page(city, keyword, pageNum, pageSize));
    }

    /** 门店 ES 检索（IK 分词 + 高亮 + 相关度排序） */
    @GetMapping("/search")
    public Result<SearchPageVO<ShopSearchVO>> search(@RequestParam(defaultValue = "1") @Min(1) @Max(100) long pageNum,
                                                     @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String city) {
        return Result.ok(searchService.searchShops(keyword, city, pageNum, pageSize));
    }

    /** 门店详情（Redis 缓存三防） */
    @GetMapping("/{id}")
    public Result<ShopVO> detail(@PathVariable Long id) {
        return Result.ok(shopService.detailCached(id));
    }

    /** 门店详情（直连 MySQL，压测对照；受 hc.perf.enabled 开关控制） */
    @GetMapping("/db/{id}")
    public Result<ShopVO> detailFromDb(@PathVariable Long id) {
        if (!perfEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "压测直连库接口未开启");
        }
        return Result.ok(shopService.detailFromDb(id));
    }

    /** 服务项目分页（DB） */
    @GetMapping("/service/page")
    public Result<SearchPageVO<ServiceItemVO>> itemPage(@RequestParam(defaultValue = "1") @Min(1) @Max(100) long pageNum,
                                                        @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize,
                                                        @RequestParam(required = false) Long shopId,
                                                        @RequestParam(required = false) String category) {
        return Result.ok(serviceItemService.page(shopId, category, pageNum, pageSize));
    }

    /** 服务项目 ES 检索 */
    @GetMapping("/service/search")
    public Result<SearchPageVO<ServiceItemSearchVO>> itemSearch(
            @RequestParam(defaultValue = "1") @Min(1) @Max(100) long pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long shopId) {
        return Result.ok(searchService.searchServiceItems(keyword, category, shopId, pageNum, pageSize));
    }

    /** 服务项目详情（Redis 缓存三防） */
    @GetMapping("/service/{id}")
    public Result<ServiceItemVO> itemDetail(@PathVariable Long id) {
        return Result.ok(serviceItemService.detailCached(id));
    }

    /** 服务项目详情（直连 MySQL，压测对照；受 hc.perf.enabled 开关控制） */
    @GetMapping("/service/db/{id}")
    public Result<ServiceItemVO> itemDetailFromDb(@PathVariable Long id) {
        if (!perfEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "压测直连库接口未开启");
        }
        return Result.ok(serviceItemService.detailFromDb(id));
    }
}