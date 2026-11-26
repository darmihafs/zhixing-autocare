package com.hongcheng.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.product.dto.ProductVO;
import com.hongcheng.product.dto.StockDeductRequest;
import com.hongcheng.product.dto.StockItem;
import com.hongcheng.product.entity.Category;
import com.hongcheng.product.entity.Product;
import com.hongcheng.product.mapper.CategoryMapper;
import com.hongcheng.product.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProductService 单元测试（mock Mapper；事务回滚行为由集成验收覆盖）。
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryMapper categoryMapper;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productMapper, categoryMapper, new ObjectMapper());
    }

    private StockDeductRequest request(long productId, int quantity) {
        StockItem item = new StockItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        StockDeductRequest request = new StockDeductRequest();
        request.setItems(List.of(item));
        return request;
    }

    private Product product(long id, int status, String images) {
        Product product = new Product();
        product.setId(id);
        product.setCategoryId(1L);
        product.setProductName("美孚1号 全合成机油 5W-30 4L");
        product.setBrand("美孚");
        product.setPrice(new BigDecimal("258.00"));
        product.setStock(200);
        product.setSales(430);
        product.setStatus(status);
        product.setImages(images);
        return product;
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setCategoryName("机油润滑油");
        category.setParentId(0L);
        return category;
    }

    @Test
    @DisplayName("扣减成功 - 调用原子 UPDATE 一次")
    void deductStock_success_callsAtomicUpdate() {
        // given
        when(productMapper.deductStock(1L, 2)).thenReturn(1);

        // when
        productService.deductStock(request(1L, 2));

        // then
        verify(productMapper).deductStock(1L, 2);
    }

    @Test
    @DisplayName("库存不足 - 影响行数0 转 1101")
    void deductStock_insufficient_throws1101() {
        // given
        when(productMapper.deductStock(1L, 999)).thenReturn(0);

        // when / then
        assertThatThrownBy(() -> productService.deductStock(request(1L, 999)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.STOCK_NOT_ENOUGH.getCode());
    }

    @Test
    @DisplayName("多明细中途失败 - 抛 1101 触发整单回滚")
    void deductStock_secondItemFails_throwsForRollback() {
        // given
        when(productMapper.deductStock(1L, 1)).thenReturn(1);
        when(productMapper.deductStock(2L, 5)).thenReturn(0);
        StockDeductRequest request = request(1L, 1);
        StockItem second = new StockItem();
        second.setProductId(2L);
        second.setQuantity(5);
        request.setItems(List.of(request.getItems().get(0), second));

        // when / then
        assertThatThrownBy(() -> productService.deductStock(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.STOCK_NOT_ENOUGH.getCode());
        verify(productMapper).deductStock(1L, 1);
        verify(productMapper).deductStock(2L, 5);
    }

    @Test
    @DisplayName("回补失败 - 影响行数0 转 1104")
    void restoreStock_failed_throws1104() {
        // given
        when(productMapper.restoreStock(1L, 5)).thenReturn(0);

        // when / then
        assertThatThrownBy(() -> productService.restoreStock(request(1L, 5)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.STOCK_RESTORE_FAILED.getCode());
    }

    @Test
    @DisplayName("详情不存在 - 抛 1102")
    void detail_missing_throws1102() {
        // given
        when(productMapper.selectById(999L)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> productService.detail(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("详情已下架 - 同样抛 1102")
    void detail_offShelf_throws1102() {
        // given
        when(productMapper.selectById(1L)).thenReturn(product(1L, 0, null));

        // when / then
        assertThatThrownBy(() -> productService.detail(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.PRODUCT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("详情成功 - images JSON 解析为列表且带分类名")
    void detail_success_parsesImagesAndCategory() {
        // given
        when(productMapper.selectById(1L)).thenReturn(product(1L, 1, "[\"a.jpg\",\"b.jpg\"]"));
        when(categoryMapper.selectById(1L)).thenReturn(category());

        // when
        ProductVO vo = productService.detail(1L);

        // then
        assertThat(vo.getImages()).containsExactly("a.jpg", "b.jpg");
        assertThat(vo.getCategoryName()).isEqualTo("机油润滑油");
        assertThat(vo.getPrice()).isEqualByComparingTo("258.00");
    }

    @Test
    @DisplayName("分页 - 记录转 VO 且批量取分类名避免 N+1")
    void page_mapsRecordsWithBatchCategoryLookup() {
        // given
        when(productMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Product> page = invocation.getArgument(0);
            page.setRecords(List.of(product(1L, 1, null)));
            page.setTotal(1);
            return page;
        });
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category()));

        // when
        Page<ProductVO> page = productService.page(null, null, 1, 10);

        // then
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getCategoryName()).isEqualTo("机油润滑油");
        assertThat(page.getRecords().get(0).getImages()).isEmpty();
        verify(categoryMapper).selectBatchIds(any());
    }

    @Test
    @DisplayName("分页空结果 - 不查分类")
    void page_empty_skipsCategoryLookup() {
        // given
        when(productMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Product> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        // when
        Page<ProductVO> page = productService.page(1L, "不存在", 1, 10);

        // then
        assertThat(page.getRecords()).isEmpty();
        verify(categoryMapper, never()).selectBatchIds(any());
    }
}