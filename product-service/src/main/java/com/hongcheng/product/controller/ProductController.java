package com.hongcheng.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.result.Result;
import com.hongcheng.product.dto.ProductVO;
import com.hongcheng.product.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品公开接口（匿名可浏览）：分页 + 详情。
 */
@Validated
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/page")
    public Result<Page<ProductVO>> page(@RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize) {
        return Result.ok(productService.page(categoryId, keyword, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }
}