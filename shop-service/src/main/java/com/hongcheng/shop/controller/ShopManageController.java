package com.hongcheng.shop.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.result.Result;
import com.hongcheng.shop.dto.ServiceItemSaveRequest;
import com.hongcheng.shop.dto.ShopSaveRequest;
import com.hongcheng.shop.entity.ServiceItem;
import com.hongcheng.shop.entity.Shop;
import com.hongcheng.shop.service.ShopManageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门店/服务项目管理端（仅允许网关透传 X-User-Role=ADMIN）。
 * 写入后：删缓存 + RocketMQ 异步同步 ES 索引。
 */
@Validated
@RestController
@RequestMapping("/manage/shop")
@RequiredArgsConstructor
public class ShopManageController {

    private final ShopManageService shopManageService;

    @GetMapping("/page")
    public Result<Page<Shop>> pageShops(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.ok(shopManageService.pageShops(keyword, status, pageNum, pageSize));
    }

    @GetMapping("/service/page")
    public Result<Page<ServiceItem>> pageServiceItems(@RequestParam(required = false) Long shopId,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
                                                      @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.ok(shopManageService.pageServiceItems(shopId, status, pageNum, pageSize));
    }

    @PostMapping
    public Result<Long> createShop(@Validated @RequestBody ShopSaveRequest request) {
        return Result.ok(shopManageService.createShop(request));
    }

    @PutMapping("/{id}")
    public Result<Void> updateShop(@PathVariable Long id, @Validated @RequestBody ShopSaveRequest request) {
        shopManageService.updateShop(id, request);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeShopStatus(@PathVariable Long id, @RequestParam Integer status) {
        shopManageService.changeShopStatus(id, status);
        return Result.ok();
    }

    @PostMapping("/service")
    public Result<Long> createServiceItem(@Validated @RequestBody ServiceItemSaveRequest request) {
        return Result.ok(shopManageService.createServiceItem(request));
    }

    @PutMapping("/service/{id}")
    public Result<Void> updateServiceItem(@PathVariable Long id,
                                          @Validated @RequestBody ServiceItemSaveRequest request) {
        shopManageService.updateServiceItem(id, request);
        return Result.ok();
    }

    @PutMapping("/service/{id}/status")
    public Result<Void> changeServiceItemStatus(@PathVariable Long id, @RequestParam Integer status) {
        shopManageService.changeServiceItemStatus(id, status);
        return Result.ok();
    }
}