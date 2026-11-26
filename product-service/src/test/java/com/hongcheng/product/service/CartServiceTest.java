package com.hongcheng.product.service;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.product.dto.CartAddRequest;
import com.hongcheng.product.dto.CartItemVO;
import com.hongcheng.product.dto.CartUpdateRequest;
import com.hongcheng.product.entity.CartItem;
import com.hongcheng.product.entity.Product;
import com.hongcheng.product.mapper.CartItemMapper;
import com.hongcheng.product.mapper.ProductMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车测试：加购合并、缺货拦截、勾选与失效条目处理。
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long PRODUCT_ID = 2001L;
    private static final Long CART_ID = 3001L;

    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private CartService cartService;

    @Test
    @DisplayName("加购 - 已在购物车时累加数量且保持勾选")
    void add_whenExisting_mergesQuantity() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(1, 10));
        CartItem existing = cartItem(CART_ID, 2, 0);
        when(cartItemMapper.selectOne(any())).thenReturn(existing);

        CartAddRequest request = new CartAddRequest();
        request.setProductId(PRODUCT_ID);
        request.setQuantity(3);

        cartService.add(USER_ID, request);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper).updateById(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        assertThat(captor.getValue().getSelected()).isEqualTo(1);
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    @DisplayName("加购 - 商品库存为 0 时拦截并提示缺货")
    void add_whenZeroStock_throwsStockNotEnough() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(1, 0));

        CartAddRequest request = new CartAddRequest();
        request.setProductId(PRODUCT_ID);
        request.setQuantity(1);

        assertThatThrownBy(() -> cartService.add(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺货");
        verify(cartItemMapper, never()).insert(any(CartItem.class));
    }

    @Test
    @DisplayName("改数量 - 超过库存时拦截")
    void update_whenQuantityExceedsStock_throws() {
        when(cartItemMapper.selectOne(any())).thenReturn(cartItem(CART_ID, 1, 1));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(1, 2));

        CartUpdateRequest request = new CartUpdateRequest();
        request.setQuantity(3);

        assertThatThrownBy(() -> cartService.update(USER_ID, CART_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足");
        verify(cartItemMapper, never()).updateById(any(CartItem.class));
    }

    @Test
    @DisplayName("勾选 - 缺货商品不允许被勾选结算")
    void update_whenSelectZeroStock_throws() {
        when(cartItemMapper.selectOne(any())).thenReturn(cartItem(CART_ID, 1, 0));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product(1, 0));

        CartUpdateRequest request = new CartUpdateRequest();
        request.setSelected(1);

        assertThatThrownBy(() -> cartService.update(USER_ID, CART_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺货");
    }

    @Test
    @DisplayName("全选 - 只勾选有货商品，缺货/失效条目保持未勾选")
    void selectAll_onlySelectsAvailableItems() {
        CartItem inStock = cartItem(1L, 1, 0);
        inStock.setProductId(11L);
        CartItem zeroStock = cartItem(2L, 1, 0);
        zeroStock.setProductId(12L);
        CartItem removed = cartItem(3L, 1, 1);
        removed.setProductId(13L);
        when(cartItemMapper.selectList(any())).thenReturn(List.of(inStock, zeroStock, removed));
        when(productMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                productWithId(11L, 1, 5),
                productWithId(12L, 1, 0)
        ));

        cartService.selectAll(USER_ID, true);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues()).extracting(CartItem::getId)
                .containsExactlyInAnyOrder(1L, 3L);
        assertThat(captor.getAllValues()).filteredOn(item -> item.getId() == 1L)
                .allMatch(item -> item.getSelected() == 1);
        assertThat(captor.getAllValues()).filteredOn(item -> item.getId() == 3L)
                .allMatch(item -> item.getSelected() == 0);
    }

    @Test
    @DisplayName("列表 - 商品被删除的条目保留并标记不可用")
    void list_whenProductMissing_marksUnavailable() {
        when(cartItemMapper.selectList(any())).thenReturn(List.of(cartItem(CART_ID, 2, 1)));
        when(productMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        List<CartItemVO> result = cartService.list(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvailable()).isFalse();
        assertThat(result.get(0).getProductName()).isEqualTo("商品已失效");
        assertThat(result.get(0).getTotalAmount()).isEqualByComparingTo("0");
    }

    private CartItem cartItem(Long id, int quantity, int selected) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setUserId(USER_ID);
        item.setProductId(PRODUCT_ID);
        item.setQuantity(quantity);
        item.setSelected(selected);
        return item;
    }

    private Product product(int status, int stock) {
        return productWithId(PRODUCT_ID, status, stock);
    }

    private Product productWithId(Long id, int status, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setProductName("测试商品" + id);
        product.setCoverImg("/images/test.jpg");
        product.setPrice(new BigDecimal("19.90"));
        product.setStatus(status);
        product.setStock(stock);
        return product;
    }
}
