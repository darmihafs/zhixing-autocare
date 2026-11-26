package com.hongcheng.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评价领域服务：资格校验、创建、分页、聚合统计、商家回复。
 * <p>
 * 资格链路：订单服务内部接口返回归属/状态/明细 → 仅本人 + 已完成 + 明细属于该单；
 * 一单一品一评由 uk_order_item 唯一索引兜底，重复提交转 1502。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int STATUS_VISIBLE = 1;
    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_IMAGES = 5;
    private static final String IMAGE_URL_PREFIX = "/api/review/uploads/";

    private final ReviewMapper reviewMapper;
    private final OrderReviewClient orderReviewClient;
    private final UserBriefClient userBriefClient;
    private final ObjectMapper objectMapper;

    public ReviewVO create(Long userId, CreateReviewRequest request) {
        OrderReviewContext context = loadReviewContext(request.getOrderNo());
        if (!context.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.REVIEW_ACCESS_DENIED);
        }
        if (!Integer.valueOf(OrderStatusEnum.COMPLETED.getCode()).equals(context.getStatus())) {
            throw new BusinessException(ResultCode.REVIEW_ORDER_NOT_COMPLETED,
                    "订单当前状态为「" + context.getStatusDesc() + "」，仅已完成订单可评价");
        }
        OrderReviewItem item = context.getItems() == null ? null : context.getItems().stream()
                .filter(candidate -> candidate.getItemId().equals(request.getItemId()))
                .findFirst()
                .orElse(null);
        if (item == null) {
            throw new BusinessException(ResultCode.REVIEW_ITEM_NOT_IN_ORDER);
        }
        long exists = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getOrderId, context.getOrderId())
                .eq(Review::getItemId, item.getItemId()));
        if (exists > 0) {
            throw new BusinessException(ResultCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = new Review();
        review.setOrderId(context.getOrderId());
        review.setOrderNo(context.getOrderNo());
        review.setUserId(userId);
        review.setShopId(context.getShopId());
        review.setItemType(item.getItemType());
        review.setItemId(item.getItemId());
        review.setScore(request.getScore());
        review.setContent(request.getContent());
        review.setImages(writeImages(request.getImages()));
        review.setLikeCount(0);
        review.setStatus(STATUS_VISIBLE);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            log.warn("重复评价被唯一索引拦截 orderNo={}, itemId={}", context.getOrderNo(), item.getItemId());
            throw new BusinessException(ResultCode.REVIEW_ALREADY_EXISTS);
        }
        log.info("评价创建成功 reviewId={}, orderNo={}, itemType={}, itemId={}, score={}, userId={}",
                review.getId(), review.getOrderNo(), review.getItemType(), review.getItemId(),
                review.getScore(), userId);
        return toVO(review);
    }

    public Page<ReviewVO> pageByItem(Integer itemType, Long itemId, long pageNum, long pageSize, Integer score) {
        validateItemType(itemType);
        validatePage(pageNum, pageSize);
        if (score != null && (score < MIN_SCORE || score > MAX_SCORE)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评分筛选范围为1~5");
        }
        Page<Review> page = reviewMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getStatus, STATUS_VISIBLE)
                        .eq(Review::getItemType, itemType)
                        .eq(Review::getItemId, itemId)
                        .eq(score != null, Review::getScore, score)
                        .orderByDesc(Review::getCreateTime));
        return toPage(page);
    }

    public Page<ReviewVO> pageByShop(Long shopId, long pageNum, long pageSize) {
        validatePage(pageNum, pageSize);
        Page<Review> page = reviewMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getStatus, STATUS_VISIBLE)
                        .eq(Review::getShopId, shopId)
                        .orderByDesc(Review::getCreateTime));
        return toPage(page);
    }

    public Page<ReviewVO> pageMine(Long userId, long pageNum, long pageSize) {
        validatePage(pageNum, pageSize);
        Page<Review> page = reviewMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getStatus, STATUS_VISIBLE)
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreateTime));
        return toPage(page);
    }

    /** 管理员评价分页：补全真实用户名/昵称；shopId 为空查全部门店（仅管理端拦截器放行后可达） */
    public Page<ReviewAdminVO> adminPage(Long shopId, long pageNum, long pageSize) {
        validatePage(pageNum, pageSize);
        Page<Review> page = reviewMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getStatus, STATUS_VISIBLE)
                        .eq(shopId != null, Review::getShopId, shopId)
                        .orderByDesc(Review::getCreateTime));
        Page<ReviewVO> voPage = toPage(page);
        Map<Long, UserBriefVO> userMap = loadUserBriefs(
                voPage.getRecords().stream().map(ReviewVO::getUserId).toList());
        Page<ReviewAdminVO> adminPage = new Page<>(voPage.getCurrent(), voPage.getSize(), voPage.getTotal());
        adminPage.setRecords(voPage.getRecords().stream()
                .map(vo -> ReviewAdminVO.from(vo, userMap.get(vo.getUserId())))
                .toList());
        return adminPage;
    }

    /** 批量拉取用户简要；降级(503)时抛业务异常——管理端必须展示真实用户名，不允许退化为匿名展示 */
    private Map<Long, UserBriefVO> loadUserBriefs(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        String ids = distinctIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        Result<List<UserBriefVO>> result = userBriefClient.briefs(ids);
        if (result == null || result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "用户服务暂不可用，无法加载管理端评价列表");
        }
        return result.getData().stream()
                .collect(Collectors.toMap(UserBriefVO::getId, user -> user, (a, b) -> a));
    }

    public ReviewVO detail(Long id) {
        return toVO(requireVisible(id));
    }

    public ReviewStatsVO stats(Integer itemType, Long itemId) {
        validateItemType(itemType);
        long total = reviewMapper.countVisible(itemType, itemId);
        BigDecimal avg = reviewMapper.avgScore(itemType, itemId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = MIN_SCORE; star <= MAX_SCORE; star++) {
            distribution.put(star, 0L);
        }
        List<ScoreCount> rows = reviewMapper.countByScore(itemType, itemId);
        if (rows != null) {
            for (ScoreCount row : rows) {
                if (row.getScore() != null) {
                    distribution.put(row.getScore(), row.getCnt() == null ? 0L : row.getCnt());
                }
            }
        }
        ReviewStatsVO vo = new ReviewStatsVO();
        vo.setItemType(itemType);
        vo.setItemId(itemId);
        vo.setTotal(total);
        vo.setAvgScore(avg == null ? BigDecimal.ZERO : avg);
        vo.setDistribution(distribution);
        return vo;
    }

    /** 门店维度评分聚合：门店评价区展示综合评分与星级分布 */
    public ReviewStatsVO statsByShop(Long shopId) {
        if (shopId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "门店ID不能为空");
        }
        long total = reviewMapper.countVisibleByShop(shopId);
        BigDecimal avg = reviewMapper.avgScoreByShop(shopId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = MIN_SCORE; star <= MAX_SCORE; star++) {
            distribution.put(star, 0L);
        }
        List<ScoreCount> rows = reviewMapper.countByScoreForShop(shopId);
        if (rows != null) {
            for (ScoreCount row : rows) {
                if (row.getScore() != null) {
                    distribution.put(row.getScore(), row.getCnt() == null ? 0L : row.getCnt());
                }
            }
        }
        ReviewStatsVO vo = new ReviewStatsVO();
        vo.setItemType(2);
        vo.setItemId(shopId);
        vo.setTotal(total);
        vo.setAvgScore(avg == null ? BigDecimal.ZERO : avg);
        vo.setDistribution(distribution);
        return vo;
    }

    /**
     * 点赞事件落库：按 reviewId 原子增减 like_count。
     * 消费端负责事件去重，这里只保证并发安全与不为负。
     *
     * @return 是否有评价被更新（评价不存在/已删除返回 false）
     */
    public boolean applyLikeDelta(Long reviewId, int delta) {
        int rows = reviewMapper.adjustLikeCount(reviewId, delta);
        if (rows == 0) {
            log.warn("点赞事件对应评价不存在或已删除 reviewId={}, delta={}", reviewId, delta);
            return false;
        }
        log.info("评价点赞数已同步 reviewId={}, delta={}", reviewId, delta);
        return true;
    }

    /** 商家回复：仅限管理员角色（网关 JWT + 下游 ADMIN 校验） */
    public ReviewVO reply(Long reviewId, Long operatorId, String reply) {
        Review review = requireVisible(reviewId);
        review.setReply(reply);
        review.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(review);
        log.info("商家回复成功 reviewId={}, operatorId={}", reviewId, operatorId);
        return toVO(review);
    }

    private OrderReviewContext loadReviewContext(String orderNo) {
        Result<OrderReviewContext> result;
        try {
            result = orderReviewClient.reviewContext(orderNo);
        } catch (Exception e) {
            log.error("查询订单评价上下文失败 orderNo={}", orderNo, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务暂不可用");
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            String message = result == null ? "订单服务无响应" : result.getMessage();
            if (result == null || result.getCode() == ResultCode.SERVICE_UNAVAILABLE.getCode()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务暂不可用");
            }
            throw new BusinessException(ResultCode.REVIEW_ORDER_NOT_COMPLETED, "订单不可评价：" + message);
        }
        return result.getData();
    }

    private Review requireVisible(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review == null || !Integer.valueOf(STATUS_VISIBLE).equals(review.getStatus())) {
            throw new BusinessException(ResultCode.REVIEW_NOT_FOUND);
        }
        return review;
    }

    private Page<ReviewVO> toPage(Page<Review> page) {
        Page<ReviewVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    private ReviewVO toVO(Review review) {
        return ReviewVO.from(review, readImages(review.getImages()));
    }

    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "最多上传 5 张评价图片");
        }
        for (String image : images) {
            if (image == null || !image.startsWith(IMAGE_URL_PREFIX)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "评价图片需通过本地上传接口获取");
            }
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "晒图数据格式非法");
        }
    }

    private List<String> readImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(images, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("评价晒图 JSON 解析失败，按空列表处理 images={}", images);
            return List.of();
        }
    }

    private void validateItemType(Integer itemType) {
        if (itemType == null || (itemType != 1 && itemType != 2)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评价对象类型仅支持1商品/2服务项目");
        }
    }

    private void validatePage(long pageNum, long pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数非法（pageSize 1~50）");
        }
    }
}
