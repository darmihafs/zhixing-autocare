package com.hongcheng.product.service;

import com.hongcheng.product.dto.CategoryVO;
import com.hongcheng.product.entity.Category;
import com.hongcheng.product.mapper.CategoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CategoryService 单元测试：内存组装分类树。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    private Category category(long id, long parentId, int sort) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setCategoryName("分类" + id);
        category.setSortOrder(sort);
        return category;
    }

    @Test
    @DisplayName("两级分类 - 子节点挂到父节点 children")
    void tree_twoLevels_attachesChildren() {
        // given
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(1L, 0L, 1), category(2L, 0L, 2), category(3L, 1L, 1)));

        // when
        List<CategoryVO> tree = categoryService.tree();

        // then
        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getId()).isEqualTo(1L);
        assertThat(tree.get(0).getChildren()).extracting(CategoryVO::getId).containsExactly(3L);
        assertThat(tree.get(1).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("空表 - 返回空树不抛异常")
    void tree_empty_returnsEmptyList() {
        // given
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        // when / then
        assertThat(categoryService.tree()).isEmpty();
    }
}