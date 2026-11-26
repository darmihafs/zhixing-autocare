package com.hongcheng.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.enums.OrderTypeEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.OrderAdminVO;
import com.hongcheng.order.dto.OrderCreateStateVO;
import com.hongcheng.order.dto.OrderPayInfoVO;
import com.hongcheng.order.dto.OrderReviewContextVO;
import com.hongcheng.order.dto.OrderReviewItemVO;
import com.hongcheng.order.dto.OrderVO;
import com.hongcheng.order.dto.UserBriefVO;
import com.hongcheng.order.entity.Order;
import com.hongcheng.order.entity.OrderItem;
import com.hongcheng.order.entity.OrderStatusLog;
import com.hongcheng.order.mapper.OrderItemMapper;
import com.hongcheng.order.mapper.OrderMapper;
import com.hongcheng.order.mapper.OrderStatusLogMapper;
import com.hongcheng.order.feign.UserBriefClient;
import com.hongcheng.order.state.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单领域服务：建单落库、详情/分页、状态机流转（支付/发货/完成/取消/超时取消）。
 * <p>
 * 所有状态变更均为「条件 UPDATE + 影响行数判断」，避免重复消息或支付/取消
 * 并发竞争时后写覆盖先写；流转成功后再写 t_order_status_log 审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 操作方：用户 */
    private static final int OPERATOR_USER = 1;
    /** 操作方：系统（支付回调/超时任务） */
    private static final int OPERATOR_SYSTEM = 2;

    private static final String CANCEL_REASON_USER = "用户取消";
    private static final String CANCEL_REASON_TIMEOUT = "超时未支付自动取消";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final OrderStateStore orderStateStore;
    private final OrderStockService orderStockService;
    private final OrderProgressPublisher orderProgressPublisher;
    private final UserBriefClient userBriefClient;

    /** 与 hc.order.pay-timeout-delay-level 对应的支付超时秒数，用于前端收银台倒计时（默认 30 分钟） */
    @Value("${hc.order.pay-timeout-seconds:1800}")
    private long payTimeoutSeconds = 1800L;

    /** MQ 消费者建单：主单 + 明细 + 首条状态日志同一事务落库 */
    @Transactional(rollbackFor = Exception.class)
    public void persist(Order order, List<OrderItem> items) {
        orderMapper.insert(order);
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            orderItemMapper.insert(item);
        }
        orderStatusLogMapper.insert(buildLog(order.getId(), order.getOrderNo(), null,
                OrderStatusEnum.PENDING_PAYMENT.getCode(), "CREATE", OPERATOR_USER, "下单请求异步创建"));
        log.info("订单落库完成 orderNo={}, itemCount={}, totalAmount={}",
                order.getOrderNo(), items.size(), order.getTotalAmount());
    }

    /** 订单详情（仅本人）；尚在队列中的 orderNo 返回「创建中/创建失败」提示 */
    public OrderVO detail(String orderNo, Long userId) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw pendingOrMissing(orderNo);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_ACCESS_DENIED);
        }
        OrderVO vo = OrderVO.from(order, listItems(order.getId()), payTimeoutSeconds);
        vo.setServerTime(LocalDateTime.now());
        return vo;
    }

    /** 下单请求处理状态（仅订单本人或 ADMIN）：DB 已建单优先，其次 Redis 暂存，最后 NOT_FOUND */
    public OrderCreateStateVO state(String orderNo, Long userId, boolean isAdmin) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            if (!isAdmin && !Objects.equals(order.getUserId(), userId)) {
                throw new BusinessException(ResultCode.ORDER_ACCESS_DENIED);
            }
            return new OrderCreateStateVO(orderNo, OrderStateStore.STATE_CREATED,
                    "订单创建成功", order.getCreateTime());
        }
        OrderCreateStateVO cached = orderStateStore.get(orderNo);
        if (cached != null) {
            if (cached.getUserId() != null && !isAdmin && !Objects.equals(cached.getUserId(), userId)) {
                throw new BusinessException(ResultCode.ORDER_ACCESS_DENIED);
            }
            return new OrderCreateStateVO(cached.getOrderNo(), cached.getState(),
                    cached.getMessage(), cached.getUpdateTime());
        }
        return new OrderCreateStateVO(orderNo, OrderStateStore.STATE_NOT_FOUND, "未找到该下单请求", null);
    }

    /** 我的订单分页（可按状态过滤），批量回填明细避免 N+1 */
    public Page<OrderVO> page(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        return queryPage(userId, status, null, pageNum, pageSize);
    }

    /** 管理员订单分页（不限归属，可按状态/类型过滤），批量回填明细避免 N+1 */
    public Page<OrderAdminVO> adminPage(Integer status, Integer orderType, Integer pageNum, Integer pageSize) {
        Page<OrderVO> page = queryPage(null, status, orderType, pageNum, pageSize);
        Map<Long, UserBriefVO> userMap = loadUserBriefs(
                page.getRecords().stream().map(OrderVO::getUserId).toList());
        Page<OrderAdminVO> adminPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        adminPage.setRecords(page.getRecords().stream()
                .map(vo -> OrderAdminVO.from(vo, userMap.get(vo.getUserId())))
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
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "用户服务暂不可用，无法加载管理端订单列表");
        }
        return result.getData().stream()
                .collect(Collectors.toMap(UserBriefVO::getId, user -> user, (a, b) -> a));
    }

    private Page<OrderVO> queryPage(Long userId, Integer status, Integer orderType,
                                    Integer pageNum, Integer pageSize) {
        Page<Order> orderPage = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Order>lambdaQuery()
                        .eq(userId != null, Order::getUserId, userId)
                        .eq(status != null, Order::getStatus, status)
                        .eq(orderType != null, Order::getOrderType, orderType)
                        .orderByDesc(Order::getCreateTime));
        List<Long> orderIds = orderPage.getRecords().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemMap = orderIds.isEmpty()
                ? Map.of()
                : orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                        .in(OrderItem::getOrderId, orderIds))
                        .stream()
                        .collect(Collectors.groupingBy(OrderItem::getOrderId));

        Page<OrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orderPage.getRecords().stream()
                .map(order -> OrderVO.from(order, itemMap.getOrDefault(order.getId(), List.of()),
                        payTimeoutSeconds))
                .toList());
        return voPage;
    }

    /** 管理员门店接单（仅服务订单）：复用 10→20 流转并推送订单进度 */
    public void adminAccept(String orderNo) {
        Order order = requireOrder(orderNo);
        if (OrderTypeEnum.SERVICE.getCode() != order.getOrderType()) {
            throw new BusinessException(ResultCode.ORDER_TYPE_UNSUPPORTED, "仅养车服务订单可接单");
        }
        markDelivered(orderNo);
        log.info("管理员门店接单完成 orderNo={}", orderNo);
    }

    /** 管理员商家发货（仅商品订单）：复用 10→20 流转并推送订单进度 */
    public void adminDeliver(String orderNo) {
        Order order = requireOrder(orderNo);
        if (OrderTypeEnum.PRODUCT.getCode() != order.getOrderType()) {
            throw new BusinessException(ResultCode.ORDER_TYPE_UNSUPPORTED, "仅商品订单可发货");
        }
        markDelivered(orderNo);
        log.info("管理员商家发货完成 orderNo={}", orderNo);
    }

    /** 用户取消：仅待支付；取消成功后 Feign 回补库存 */
    public void cancel(String orderNo, Long userId) {
        Order order = requireOwnedOrder(orderNo, userId);
        if (OrderStatusEnum.CANCELLED.getCode() == order.getStatus()) {
            log.info("订单已取消，幂等返回 orderNo={}", orderNo);
            return;
        }
        OrderStateMachine.assertTransit(order.getStatus(), OrderStatusEnum.CANCELLED.getCode());
        int rows = orderMapper.markCancelled(order.getId(), OrderStatusEnum.PENDING_PAYMENT.getCode(),
                CANCEL_REASON_USER, LocalDateTime.now());
        if (rows == 0) {
            Order latest = orderMapper.selectByOrderNo(orderNo);
            if (latest != null && OrderStatusEnum.CANCELLED.getCode() == latest.getStatus()) {
                log.info("订单已取消，幂等返回 orderNo={}", orderNo);
                return;
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，取消失败");
        }
        orderStatusLogMapper.insert(buildLog(order.getId(), orderNo,
                OrderStatusEnum.PENDING_PAYMENT.getCode(), OrderStatusEnum.CANCELLED.getCode(),
                "CANCEL_USER", OPERATOR_USER, CANCEL_REASON_USER));
        orderStockService.restoreWithRetry(listItems(order.getId()), orderNo);
        orderProgressPublisher.publish(order, "ORDER_CANCELLED", OrderStatusEnum.CANCELLED.getCode(),
                "订单已取消，库存已释放");
        log.info("用户取消订单成功 orderNo={}, userId={}", orderNo, userId);
    }

    /** 用户确认完成：仅服务中/已发货 */
    public void complete(String orderNo, Long userId) {
        Order order = requireOwnedOrder(orderNo, userId);
        OrderStateMachine.assertTransit(order.getStatus(), OrderStatusEnum.COMPLETED.getCode());
        int rows = orderMapper.markCompleted(order.getId(), LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，完成失败");
        }
        orderStatusLogMapper.insert(buildLog(order.getId(), orderNo,
                OrderStatusEnum.IN_SERVICE.getCode(), OrderStatusEnum.COMPLETED.getCode(),
                "FINISH", OPERATOR_USER, "用户确认完成"));
        orderProgressPublisher.publish(order, "ORDER_COMPLETED", OrderStatusEnum.COMPLETED.getCode(),
                "订单已完成，可以发布评价了");
        log.info("订单已完成 orderNo={}, userId={}", orderNo, userId);
    }

    /** 支付成功回调（pay-service 内部调用）：0→10，重复回调幂等 */
    public void markPaid(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (OrderStatusEnum.PAID.getCode() == order.getStatus()) {
            log.info("订单已支付，忽略重复回调 orderNo={}", orderNo);
            return;
        }
        OrderStateMachine.assertTransit(order.getStatus(), OrderStatusEnum.PAID.getCode());
        int rows = orderMapper.markPaid(order.getId(), LocalDateTime.now());
        if (rows == 0) {
            Order latest = orderMapper.selectByOrderNo(orderNo);
            if (latest != null && OrderStatusEnum.PAID.getCode() == latest.getStatus()) {
                log.info("支付回调并发重复，幂等返回 orderNo={}", orderNo);
                return;
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，支付回调忽略");
        }
        orderStatusLogMapper.insert(buildLog(order.getId(), orderNo,
                OrderStatusEnum.PENDING_PAYMENT.getCode(), OrderStatusEnum.PAID.getCode(),
                "PAY", OPERATOR_SYSTEM, "支付成功回调"));
        orderProgressPublisher.publish(order, "ORDER_PAID", OrderStatusEnum.PAID.getCode(),
                "支付成功，等待商家接单/发货");
        log.info("订单支付状态已更新 orderNo={}", orderNo);
    }

    /**
     * 演示用商家发货：校验订单归属后复用 10→20 状态流转。
     * <p>
     * 不向浏览器暴露内部令牌，只允许订单本人在演示环境触发。
     */
    public void demoDeliver(String orderNo, Long userId) {
        requireOwnedOrder(orderNo, userId);
        markDelivered(orderNo);
        log.info("演示发货完成 orderNo={}, userId={}", orderNo, userId);
    }

    /** 发货/进入服务（商家或内部系统调用）：10→20，重复调用幂等 */
    public void markDelivered(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (OrderStatusEnum.IN_SERVICE.getCode() == order.getStatus()) {
            log.info("订单已发货，忽略重复操作 orderNo={}", orderNo);
            return;
        }
        OrderStateMachine.assertTransit(order.getStatus(), OrderStatusEnum.IN_SERVICE.getCode());
        int rows = orderMapper.markDelivered(order.getId());
        if (rows == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单状态已变更，发货失败");
        }
        orderStatusLogMapper.insert(buildLog(order.getId(), orderNo,
                OrderStatusEnum.PAID.getCode(), OrderStatusEnum.IN_SERVICE.getCode(),
                "DELIVER", OPERATOR_SYSTEM, "商家发货/进入服务"));
        orderProgressPublisher.publish(order, "ORDER_DELIVERED", OrderStatusEnum.IN_SERVICE.getCode(),
                "商家已接单/发货，订单进入服务中");
        log.info("订单已进入服务中/已发货 orderNo={}", orderNo);
    }

    /**
     * 延迟消息触发：仅待支付订单置为已取消并回补库存。
     * <p>
     * 已支付/已取消的订单直接忽略（支付与超时竞争时，条件 UPDATE 只会有一方成功）。
     */
    public void timeoutCancel(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("超时消息对应订单不存在，忽略 orderNo={}", orderNo);
            return;
        }
        if (OrderStatusEnum.PENDING_PAYMENT.getCode() != order.getStatus()) {
            log.info("订单状态={}，无需超时取消 orderNo={}", order.getStatus(), orderNo);
            return;
        }
        int rows = orderMapper.markCancelled(order.getId(), OrderStatusEnum.PENDING_PAYMENT.getCode(),
                CANCEL_REASON_TIMEOUT, LocalDateTime.now());
        if (rows == 0) {
            log.info("超时取消与其它状态变更并发，条件更新未命中，忽略 orderNo={}", orderNo);
            return;
        }
        orderStatusLogMapper.insert(buildLog(order.getId(), orderNo,
                OrderStatusEnum.PENDING_PAYMENT.getCode(), OrderStatusEnum.CANCELLED.getCode(),
                "CANCEL_TIMEOUT", OPERATOR_SYSTEM, CANCEL_REASON_TIMEOUT));
        orderStockService.restoreWithRetry(listItems(order.getId()), orderNo);
        orderProgressPublisher.publish(order, "ORDER_TIMEOUT_CANCELLED", OrderStatusEnum.CANCELLED.getCode(),
                "超时未支付，订单已自动取消");
        log.info("超时未支付，订单已自动取消 orderNo={}", orderNo);
    }

    /**
     * 内部查询：支付服务创建支付单前校验订单归属、应付金额与可支付状态。
     * <p>
     * 不做用户归属校验（调用方 pay-service 已完成），仅要求订单已落库。
     */
    public OrderPayInfoVO payInfo(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return OrderPayInfoVO.from(order);
    }

    /**
     * 内部查询：评价服务校验「已完成订单 + 明细归属」。
     * <p>
     * 返回订单归属、状态与明细快照；不在此处判断用户/状态，由 review-service 统一给出 15xx 业务码。
     */
    public OrderReviewContextVO reviewContext(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        List<OrderReviewItemVO> items = listItems(order.getId()).stream()
                .map(OrderReviewItemVO::from)
                .toList();
        return OrderReviewContextVO.of(order.getId(), order.getOrderNo(), order.getUserId(),
                order.getShopId(), order.getOrderType(), order.getStatus(), items);
    }

    private Order requireOwnedOrder(String orderNo, Long userId) {
        Order order = requireOrder(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ORDER_ACCESS_DENIED);
        }
        return order;
    }

    /** 管理员操作：仅要求订单已落库，不做归属校验（角色由拦截器把关） */
    private Order requireOrder(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private BusinessException pendingOrMissing(String orderNo) {
        OrderCreateStateVO cached = orderStateStore.get(orderNo);
        if (cached != null && OrderStateStore.STATE_QUEUED.equals(cached.getState())) {
            return new BusinessException(ResultCode.ORDER_CREATING);
        }
        if (cached != null && OrderStateStore.STATE_FAILED.equals(cached.getState())) {
            return new BusinessException(ResultCode.ORDER_CREATE_FAILED, cached.getMessage());
        }
        return new BusinessException(ResultCode.ORDER_NOT_FOUND);
    }

    private List<OrderItem> listItems(Long orderId) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId));
    }

    private OrderStatusLog buildLog(Long orderId, String orderNo, Integer fromStatus, Integer toStatus,
                                    String event, int operatorType, String remark) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setOrderNo(orderNo);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus);
        statusLog.setEvent(event);
        statusLog.setOperatorType(operatorType);
        statusLog.setRemark(remark);
        return statusLog;
    }
}
