package com.hongcheng.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.product.dto.CartAddRequest;
import com.hongcheng.product.dto.CartItemVO;
import com.hongcheng.product.dto.CartUpdateRequest;
import com.hongcheng.product.entity.CartItem;
import com.hongcheng.product.entity.Product;
import com.hongcheng.product.mapper.CartItemMapper;
import com.hongcheng.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 持久化购物车：加购、改数量、勾选、批量删除。
 * <p>
 * 价格与库存始终取商品当前值，不落快照；商品下架/删除后条目保留但标记不可结算，
 * 避免用户购物车被静默清空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final int MAX_QUANTITY = 999;

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    public List<CartItemVO> list(Long userId) {
        List<CartItem> cartItems = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreateTime));
        if (cartItems.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> products = productMapper.selectBatchIds(cartItems.stream()
                        .map(CartItem::getProductId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        return cartItems.stream()
                .map(item -> toVO(item, products.get(item.getProductId())))
                .toList();
    }

    /** 购物车角标：所有条目数量之和 */
    public int count(Long userId) {
        return cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId))
                .stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    public void add(Long userId, CartAddRequest request) {
        Product product = requirePurchasableProduct(request.getProductId());
        CartItem existing = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, request.getProductId()));
        int targetQuantity = request.getQuantity();
        if (existing != null) {
            targetQuantity += existing.getQuantity();
        }
        if (targetQuantity > MAX_QUANTITY) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单件商品最多加购999件");
        }
        if (product.getStock() != null && targetQuantity > product.getStock()) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                    "库存不足，当前仅剩" + product.getStock() + "件");
        }
        if (existing == null) {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(request.getProductId());
            item.setQuantity(request.getQuantity());
            item.setSelected(1);
            try {
                cartItemMapper.insert(item);
                return;
            } catch (DuplicateKeyException e) {
                // 并发加购命中唯一索引：转为累加已有条目
                existing = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getProductId, request.getProductId()));
                if (existing == null) {
                    throw e;
                }
                targetQuantity = existing.getQuantity() + request.getQuantity();
                if (targetQuantity > MAX_QUANTITY
                        || (product.getStock() != null && targetQuantity > product.getStock())) {
                    throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "库存不足");
                }
            }
        }
        existing.setQuantity(targetQuantity);
        existing.setSelected(1);
        cartItemMapper.updateById(existing);
    }

    public void update(Long userId, Long id, CartUpdateRequest request) {
        if (request.getQuantity() == null && request.getSelected() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请至少修改数量或勾选状态");
        }
        if (request.getSelected() != null
                && request.getSelected() != 0 && request.getSelected() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "勾选状态只能为0或1");
        }
        CartItem item = requireOwnedItem(userId, id);
        if (request.getQuantity() != null) {
            Product product = requirePurchasableProduct(item.getProductId());
            if (product.getStock() != null && request.getQuantity() > product.getStock()) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                        "库存不足，当前仅剩" + product.getStock() + "件");
            }
            item.setQuantity(request.getQuantity());
        }
        if (request.getSelected() != null) {
            if (request.getSelected() == 1) {
                requirePurchasableProduct(item.getProductId());
            }
            item.setSelected(request.getSelected());
        }
        cartItemMapper.updateById(item);
    }

    public void remove(Long userId, Long id) {
        int rows = cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getId, id)
                .eq(CartItem::getUserId, userId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND);
        }
    }

    /** 全选/取消全选：全选只勾选当前仍可购买的商品，避免失效条目阻塞结算 */
    public void selectAll(Long userId, boolean selected) {
        List<CartItem> items = cartItemMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId));
        if (items.isEmpty()) {
            return;
        }
        Map<Long, Product> products = productMapper.selectBatchIds(items.stream()
                        .map(CartItem::getProductId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        for (CartItem item : items) {
            Product product = products.get(item.getProductId());
            int target = selected && isAvailable(product) ? 1 : 0;
            if (!Integer.valueOf(target).equals(item.getSelected())) {
                item.setSelected(target);
                cartItemMapper.updateById(item);
            }
        }
    }

    /** 下单成功后移除已勾选条目 */
    public int removeSelected(Long userId) {
        return cartItemMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, 1));
    }

    private CartItem requireOwnedItem(Long userId, Long id) {
        CartItem item = cartItemMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getId, id)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND);
        }
        return item;
    }

    private Product requirePurchasableProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStock() == null || product.getStock() <= 0) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH, "该商品暂时缺货");
        }
        return product;
    }

    private boolean isAvailable(Product product) {
        return product != null
                && Integer.valueOf(1).equals(product.getStatus())
                && product.getStock() != null
                && product.getStock() > 0;
    }

    private CartItemVO toVO(CartItem item, Product product) {
        CartItemVO vo = new CartItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setQuantity(item.getQuantity());
        vo.setSelected(Integer.valueOf(1).equals(item.getSelected()));
        boolean available = isAvailable(product);
        vo.setAvailable(available);
        if (product == null) {
            vo.setProductName("商品已失效");
            vo.setPrice(BigDecimal.ZERO);
            vo.setStock(0);
            vo.setTotalAmount(BigDecimal.ZERO);
            return vo;
        }
        vo.setProductName(product.getProductName());
        vo.setCoverImg(product.getCoverImg());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setTotalAmount(available
                ? product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                : BigDecimal.ZERO);
        return vo;
    }
}
