package com.hongcheng.product.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.product.dto.CategoryVO;
import com.hongcheng.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口（公开浏览，网关 /api/product/** 前缀放行）。
 */
@RestController
@RequestMapping("/product/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }
}