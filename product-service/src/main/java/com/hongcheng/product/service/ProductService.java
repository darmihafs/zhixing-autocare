package com.hongcheng.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品查询与库存操作。
 * <p>
 * 扣减/回补整单 @Transactional：任一明细影响行数 0 即抛业务异常回滚全部，
 * 单明细的原子性由 SQL 的 stock >= n 条件保证（行锁内判定，无先查后改窗口）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    /** 公开分页：仅上架商品，支持分类与关键词（名称/品牌）筛选，按销量倒序 */
    public Page<ProductVO> page(Long categoryId, String keyword, long pageNum, long pageSize) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Product::getProductName, keyword)
                        .or()
                        .like(Product::getBrand, keyword))
                .orderByDesc(Product::getSales);
        Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<Long, String> categoryNames = categoryNames(page.getRecords());
        Page<ProductVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(product -> toVO(product, categoryNames.get(product.getCategoryId())))
                .toList());
        return voPage;
    }

    /** 商品详情：不存在或下架统一 1102 */
    public ProductVO detail(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        Category category = categoryMapper.selectById(product.getCategoryId());
        return toVO(product, category == null ? null : category.getCategoryName());
    }

    /** 扣库存（订单创建联动）：整单事务，库存不足抛 1101 回滚 */
    @Transactional(rollbackFor = Exception.class)
    public void deductStock(StockDeductRequest request) {
        for (StockItem item : request.getItems()) {
            int rows = productMapper.deductStock(item.getProductId(), item.getQuantity());
            if (rows == 0) {
                log.warn("库存扣减失败 productId={}, quantity={}", item.getProductId(), item.getQuantity());
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH,
                        "库存不足: 商品" + item.getProductId());
            }
        }
        log.info("库存扣减成功 items={}", request.getItems().size());
    }

    /** 回补库存（订单取消联动）：整单事务，销量不足抛 1104 回滚 */
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(StockDeductRequest request) {
        for (StockItem item : request.getItems()) {
            int rows = productMapper.restoreStock(item.getProductId(), item.getQuantity());
            if (rows == 0) {
                log.warn("库存回补失败 productId={}, quantity={}", item.getProductId(), item.getQuantity());
                throw new BusinessException(ResultCode.STOCK_RESTORE_FAILED,
                        "库存回补失败: 商品" + item.getProductId());
            }
        }
        log.info("库存回补成功 items={}", request.getItems().size());
    }

    /** 批量取分类名，避免分页逐条查分类（N+1） */
    private Map<Long, String> categoryNames(List<Product> products) {
        Set<Long> ids = products.stream().map(Product::getCategoryId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getCategoryName, (a, b) -> a));
    }

    private ProductVO toVO(Product product, String categoryName) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setProductName(product.getProductName());
        vo.setBrand(product.getBrand());
        vo.setCoverImg(product.getCoverImg());
        vo.setImages(parseImages(product.getImages()));
        vo.setDescription(product.getDescription());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setStatus(product.getStatus());
        vo.setCreateTime(product.getCreateTime());
        return vo;
    }

    private List<String> parseImages(String images) {
        if (!StringUtils.hasText(images)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(images, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("商品轮播图JSON解析失败: {}", images);
            return List.of();
        }
    }
}