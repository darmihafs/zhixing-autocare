package com.hongcheng.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.review.dto.CreateReviewRequest;
import com.hongcheng.review.dto.OrderReviewContext;
import com.hongcheng.review.dto.OrderReviewItem;
import com.hongcheng.review.dto.ReviewAdminVO;
import com.hongcheng.review.dto.ReviewStatsVO;
import com.hongcheng.review.dto.ReviewVO;
import com.hongcheng.review.dto.ScoreCount;
import com.hongcheng.review.dto.UserBriefVO;
import com.hongcheng.review.entity.Review;
import com.hongcheng.review.feign.OrderReviewClient;
import com.hongcheng.review.feign.UserBriefClient;
import com.hongcheng.review.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评价领域服务测试：资格校验、唯一约束、分页、聚合统计、商家回复。
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final String ORDER_NO = "HC900000000000000001";
    private static final Long ORDER_ID = 900000000000000001L;
    private static final Long USER_ID = 1L;
    private static final Long ITEM_ID = 92001L;

    @Mock
    private ReviewMapper reviewMapper;

    @Mock
    private OrderReviewClient orderReviewClient;

    @Mock
    private UserBriefClient userBriefClient;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewMapper, orderReviewClient, userBriefClient, new ObjectMapper());
    }

    @Test
    @DisplayName("发布评价 - 已完成订单且明细匹配时落库并序列化晒图")
    void create_whenEligible_insertsReview() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.ok(context(30, USER_ID, ITEM_ID)));
        when(reviewMapper.selectCount(any())).thenReturn(0L);

        ReviewVO vo = reviewService.create(USER_ID, request());

        assertThat(vo.getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(vo.getScore()).isEqualTo(5);
        assertThat(vo.getImages()).containsExactly("/api/review/uploads/20261116/a.jpg", "/api/review/uploads/20261116/b.jpg");
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).insert(captor.capture());
        Review saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getItemType()).isEqualTo(1);
        assertThat(saved.getItemId()).isEqualTo(ITEM_ID);
        assertThat(saved.getShopId()).isEqualTo(3L);
        assertThat(saved.getImages()).isEqualTo("[\"/api/review/uploads/20261116/a.jpg\",\"/api/review/uploads/20261116/b.jpg\"]");
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("发布评价 - 已评价过抛 1502 且不落库")
    void create_whenAlreadyReviewed_throwsAlreadyExists() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.ok(context(30, USER_ID, ITEM_ID)));
        when(reviewMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> reviewService.create(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1502));
        verify(reviewMapper, never()).insert(any(Review.class));
    }

    @Test
    @DisplayName("发布评价 - 唯一索引并发冲突转 1502")
    void create_whenDuplicateKey_throwsAlreadyExists() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.ok(context(30, USER_ID, ITEM_ID)));
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.insert(any(Review.class))).thenThrow(new DuplicateKeyException("uk_order_item"));

        assertThatThrownBy(() -> reviewService.create(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1502));
    }

    @Test
    @DisplayName("发布评价 - 订单未完成抛 1503")
    void create_whenOrderNotCompleted_throwsNotCompleted() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.ok(context(10, USER_ID, ITEM_ID)));

        assertThatThrownBy(() -> reviewService.create(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1503));
    }

    @Test
    @DisplayName("发布评价 - 非订单本人抛 1505")
    void create_whenNotOwner_throwsAccessDenied() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.ok(context(30, 2L, ITEM_ID)));

        assertThatThrownBy(() -> reviewService.create(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1505));
    }

    @Test
    @DisplayName("发布评价 - 商品不在订单中抛 1504")
    void create_whenItemNotInOrder_throwsItemNotInOrder() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.ok(context(30, USER_ID, 99999L)));

        assertThatThrownBy(() -> reviewService.create(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1504));
    }

    @Test
    @DisplayName("发布评价 - 订单不存在转 1503")
    void create_whenOrderMissing_throwsNotCompleted() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.fail(ResultCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> reviewService.create(USER_ID, request()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getCode()).isEqualTo(1503);
                    assertThat(e.getMessage()).contains("订单不存在");
                });
    }

    @Test
    @DisplayName("商品评价分页 - 解析晒图并返回分页元数据")
    void pageByItem_whenHasData_returnsParsedPage() {
        Review review = review(1);
        Page<Review> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(review));
        when(reviewMapper.selectPage(any(), any())).thenReturn(page);

        Page<ReviewVO> result = reviewService.pageByItem(1, ITEM_ID, 1, 10, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getImages()).containsExactly("/api/review/uploads/20261116/a.jpg");
        assertThat(result.getRecords().get(0).getScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("评分聚合 - 平均分与 1~5 星分布完整")
    void stats_whenReviewsExist_returnsAggregation() {
        when(reviewMapper.countVisible(1, ITEM_ID)).thenReturn(3L);
        when(reviewMapper.avgScore(1, ITEM_ID)).thenReturn(new BigDecimal("4.0"));
        ScoreCount five = scoreCount(5, 1L);
        ScoreCount four = scoreCount(4, 1L);
        ScoreCount three = scoreCount(3, 1L);
        when(reviewMapper.countByScore(1, ITEM_ID)).thenReturn(List.of(five, four, three));

        ReviewStatsVO stats = reviewService.stats(1, ITEM_ID);

        assertThat(stats.getTotal()).isEqualTo(3);
        assertThat(stats.getAvgScore()).isEqualByComparingTo("4.0");
        assertThat(stats.getDistribution()).hasSize(5);
        assertThat(stats.getDistribution().get(5)).isEqualTo(1L);
        assertThat(stats.getDistribution().get(1)).isEqualTo(0L);
    }

    @Test
    @DisplayName("门店评分聚合 - 返回平均分与星级分布")
    void statsByShop_whenReviewsExist_returnsAggregation() {
        when(reviewMapper.countVisibleByShop(3L)).thenReturn(2L);
        when(reviewMapper.avgScoreByShop(3L)).thenReturn(new BigDecimal("4.5"));
        when(reviewMapper.countByScoreForShop(3L))
                .thenReturn(List.of(scoreCount(5, 1L), scoreCount(4, 1L)));

        ReviewStatsVO stats = reviewService.statsByShop(3L);

        assertThat(stats.getItemType()).isEqualTo(2);
        assertThat(stats.getItemId()).isEqualTo(3L);
        assertThat(stats.getTotal()).isEqualTo(2);
        assertThat(stats.getAvgScore()).isEqualByComparingTo("4.5");
        assertThat(stats.getDistribution().get(5)).isEqualTo(1L);
        assertThat(stats.getDistribution().get(4)).isEqualTo(1L);
        assertThat(stats.getDistribution().get(1)).isEqualTo(0L);
    }

    @Test
    @DisplayName("发布评价 - 非本地上传图片被拒绝")
    void create_whenExternalImage_throwsBadRequest() {
        when(orderReviewClient.reviewContext(ORDER_NO)).thenReturn(Result.ok(context(30, USER_ID, ITEM_ID)));
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        CreateReviewRequest request = request();
        request.setImages(List.of("http://evil.example.com/a.jpg"));

        assertThatThrownBy(() -> reviewService.create(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地上传");
        verify(reviewMapper, never()).insert(any(Review.class));
    }

    @Test
    @DisplayName("商家回复 - 写入回复内容与时间")
    void reply_whenExists_updatesReply() {
        Review review = review(1);
        when(reviewMapper.selectById(1L)).thenReturn(review);

        ReviewVO vo = reviewService.reply(1L, 1L, "感谢您的评价");

        assertThat(vo.getReply()).isEqualTo("感谢您的评价");
        assertThat(vo.getReplyTime()).isNotNull();
        verify(reviewMapper).updateById(review);
    }

    @Test
    @DisplayName("商家回复 - 评价不存在抛 1501")
    void reply_whenMissing_throwsNotFound() {
        when(reviewMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> reviewService.reply(1L, 1L, "回复"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1501));
    }

    @Test
    @DisplayName("分页参数非法 - 抛 400")
    void pageByItem_whenPageSizeTooLarge_throwsBadRequest() {
        assertThatThrownBy(() -> reviewService.pageByItem(1, ITEM_ID, 1, 100, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(400));
    }

    private CreateReviewRequest request() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setOrderNo(ORDER_NO);
        request.setItemId(ITEM_ID);
        request.setScore(5);
        request.setContent("服务很好");
        request.setImages(List.of("/api/review/uploads/20261116/a.jpg", "/api/review/uploads/20261116/b.jpg"));
        return request;
    }

    private OrderReviewContext context(int status, Long userId, Long itemId) {
        OrderReviewItem item = new OrderReviewItem();
        item.setItemType(1);
        item.setItemId(itemId);
        item.setItemName("STEP8-REVIEW-PRODUCT");
        item.setQuantity(1);

        OrderReviewContext context = new OrderReviewContext();
        context.setOrderId(ORDER_ID);
        context.setOrderNo(ORDER_NO);
        context.setUserId(userId);
        context.setShopId(3L);
        context.setOrderType(1);
        context.setStatus(status);
        context.setStatusDesc(OrderStatusEnum.of(status).getDesc());
        context.setItems(List.of(item));
        return context;
    }

    private Review review(int status) {
        Review review = new Review();
        review.setId(1L);
        review.setOrderId(ORDER_ID);
        review.setOrderNo(ORDER_NO);
        review.setUserId(USER_ID);
        review.setShopId(3L);
        review.setItemType(1);
        review.setItemId(ITEM_ID);
        review.setScore(5);
        review.setContent("服务很好");
        review.setImages("[\"/api/review/uploads/20261116/a.jpg\"]");
        review.setLikeCount(0);
        review.setStatus(status);
        review.setCreateTime(LocalDateTime.now());
        return review;
    }

    private ScoreCount scoreCount(int score, Long cnt) {
        ScoreCount row = new ScoreCount();
        row.setScore(score);
        row.setCnt(cnt);
        return row;
    }

    @Test
    @DisplayName("点赞数同步 - 命中评价时返回 true 并原子增减")
    void applyLikeDelta_whenRowUpdated_returnsTrue() {
        when(reviewMapper.adjustLikeCount(1L, 1)).thenReturn(1);

        assertThat(reviewService.applyLikeDelta(1L, 1)).isTrue();

        verify(reviewMapper).adjustLikeCount(1L, 1);
    }

    @Test
    @DisplayName("点赞数同步 - 评价不存在或已删除时返回 false")
    void applyLikeDelta_whenNoRow_returnsFalse() {
        when(reviewMapper.adjustLikeCount(404L, -1)).thenReturn(0);

        assertThat(reviewService.applyLikeDelta(404L, -1)).isFalse();
    }

    @Test
    @DisplayName("管理员评价分页 - 补全真实用户名/昵称（C 端分页接口不受影响）")
    void adminPage_enrichesUsernameAndNickname() {
        Page<Review> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(review(1)));
        when(reviewMapper.selectPage(any(), any())).thenReturn(page);
        UserBriefVO brief = new UserBriefVO();
        brief.setId(USER_ID);
        brief.setUsername("zhangsan");
        brief.setNickname("张三");
        when(userBriefClient.briefs("1")).thenReturn(Result.ok(List.of(brief)));

        Page<ReviewAdminVO> result = reviewService.adminPage(3L, 1, 10);

        ReviewAdminVO vo = result.getRecords().get(0);
        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        assertThat(vo.getUsername()).isEqualTo("zhangsan");
        assertThat(vo.getNickname()).isEqualTo("张三");
        assertThat(vo.getContent()).isEqualTo("服务很好");
    }

    @Test
    @DisplayName("管理员评价分页 - 用户简要降级 503 时抛业务异常，不退化为匿名展示")
    void adminPage_whenBriefFallback_throwsServiceUnavailable() {
        Page<Review> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(review(1)));
        when(reviewMapper.selectPage(any(), any())).thenReturn(page);
        when(userBriefClient.briefs(any()))
                .thenReturn(Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "用户服务繁忙"));

        assertThatThrownBy(() -> reviewService.adminPage(null, 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户服务暂不可用");
    }
}
