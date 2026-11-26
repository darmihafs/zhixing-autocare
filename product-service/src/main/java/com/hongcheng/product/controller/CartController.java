package com.hongcheng.product.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.product.dto.CartAddRequest;
import com.hongcheng.product.dto.CartItemVO;
import com.hongcheng.product.dto.CartUpdateRequest;
import com.hongcheng.product.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口（需登录）：网关 /api/cart/** 强制 JWT，下游信任 X-User-Id。
 */
@Validated
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/list")
    public Result<List<CartItemVO>> list(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.ok(cartService.list(userId));
    }

    @GetMapping("/count")
    public Result<Integer> count(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.ok(cartService.count(userId));
    }

    @PostMapping
    public Result<Void> add(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                            @Valid @RequestBody CartAddRequest request) {
        cartService.add(userId, request);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Long id,
                               @Valid @RequestBody CartUpdateRequest request) {
        cartService.update(userId, id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Long id) {
        cartService.remove(userId, id);
        return Result.ok();
    }

    @PutMapping("/select-all")
    public Result<Void> selectAll(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                  @RequestParam(defaultValue = "true") Boolean selected) {
        cartService.selectAll(userId, Boolean.TRUE.equals(selected));
        return Result.ok();
    }

    @DeleteMapping("/selected")
    public Result<Integer> removeSelected(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.ok(cartService.removeSelected(userId));
    }
}
