package com.hongcheng.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongcheng.product.dto.CategoryVO;
import com.hongcheng.product.entity.Category;
import com.hongcheng.product.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类树查询（一次全表查询 + 内存组装，分类量级小）。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    /** 顶级分类列表，children 递归挂载，按 sortOrder 排序 */
    public List<CategoryVO> tree() {
        List<CategoryVO> nodes = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSortOrder)
                        .orderByAsc(Category::getId))
                .stream()
                .map(this::toVO)
                .toList();
        Map<Long, List<CategoryVO>> byParent = nodes.stream()
                .collect(Collectors.groupingBy(CategoryVO::getParentId));
        nodes.forEach(node -> node.setChildren(byParent.getOrDefault(node.getId(), List.of())));
        return byParent.getOrDefault(0L, List.of());
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setCategoryName(category.getCategoryName());
        vo.setParentId(category.getParentId());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }
}