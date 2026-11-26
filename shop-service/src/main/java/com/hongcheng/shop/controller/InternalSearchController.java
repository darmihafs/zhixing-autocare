package com.hongcheng.shop.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.shop.service.EsIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部运维接口：ES 索引全量重建（删除 -> 建索引 -> 从 DB 灌全量）。
 * 网关不路由 /internal/**，需携带 X-Internal-Token。
 */
@RestController
@RequestMapping("/internal/shop/search")
@RequiredArgsConstructor
public class InternalSearchController {

    private final EsIndexService esIndexService;

    @PostMapping("/reindex")
    public Result<Void> reindex() {
        esIndexService.reindexAll();
        return Result.ok();
    }
}