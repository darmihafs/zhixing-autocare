package com.hongcheng.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongcheng.order.entity.Order;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 订单 Mapper。
 * <p>
 * 状态流转一律使用「UPDATE ... WHERE status = 原状态」的条件更新：
 * 影响行数为 0 说明状态已被并发修改（重复消息/支付与取消竞争），
 * 由服务层决定幂等返回还是抛业务异常，避免后写覆盖先写。
 */
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM t_order WHERE order_no = #{orderNo} AND deleted = 0 LIMIT 1")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    /** 0待支付 → 10已支付 */
    @Update("UPDATE t_order SET status = 10, pay_time = #{payTime}, update_time = NOW() "
            + "WHERE id = #{id} AND status = 0 AND deleted = 0")
    int markPaid(@Param("id") Long id, @Param("payTime") LocalDateTime payTime);

    /** 10已支付 → 20服务中/已发货 */
    @Update("UPDATE t_order SET status = 20, update_time = NOW() "
            + "WHERE id = #{id} AND status = 10 AND deleted = 0")
    int markDelivered(@Param("id") Long id);

    /** 20服务中/已发货 → 30已完成 */
    @Update("UPDATE t_order SET status = 30, finish_time = #{finishTime}, update_time = NOW() "
            + "WHERE id = #{id} AND status = 20 AND deleted = 0")
    int markCompleted(@Param("id") Long id, @Param("finishTime") LocalDateTime finishTime);

    /** 待支付 → 40已取消（用户取消 / 超时取消） */
    @Update("UPDATE t_order SET status = 40, cancel_time = #{cancelTime}, cancel_reason = #{reason}, update_time = NOW() "
            + "WHERE id = #{id} AND status = #{fromStatus} AND deleted = 0")
    int markCancelled(@Param("id") Long id,
                      @Param("fromStatus") Integer fromStatus,
                      @Param("reason") String reason,
                      @Param("cancelTime") LocalDateTime cancelTime);
}
