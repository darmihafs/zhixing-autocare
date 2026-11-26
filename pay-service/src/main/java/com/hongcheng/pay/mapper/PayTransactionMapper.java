package com.hongcheng.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongcheng.pay.entity.PayTransaction;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 支付流水 Mapper：状态变更全部为条件 UPDATE，配合 out_trade_no 唯一索引保证回调幂等。
 */
public interface PayTransactionMapper extends BaseMapper<PayTransaction> {

    @Select("SELECT * FROM t_pay_transaction WHERE out_trade_no = #{outTradeNo}")
    PayTransaction selectByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    /** 同一订单当前支付中流水（创建支付单时复用，避免重复发起） */
    @Select("SELECT * FROM t_pay_transaction WHERE order_no = #{orderNo} AND status = 0 "
            + "ORDER BY create_time DESC, id DESC LIMIT 1")
    PayTransaction selectOpenByOrderNo(@Param("orderNo") String orderNo);

    /** 支付成功：仅 0支付中 → 1成功，返回 0 表示已被并发回调处理 */
    @Update("UPDATE t_pay_transaction SET status = 1, third_trade_no = #{thirdTradeNo}, "
            + "pay_time = #{payTime}, callback_time = #{callbackTime} "
            + "WHERE id = #{id} AND status = 0")
    int markSuccess(@Param("id") Long id,
                    @Param("thirdTradeNo") String thirdTradeNo,
                    @Param("payTime") LocalDateTime payTime,
                    @Param("callbackTime") LocalDateTime callbackTime);

    /** 支付失败/订单不可支付：仅 0支付中 → 2失败 */
    @Update("UPDATE t_pay_transaction SET status = 2, third_trade_no = #{thirdTradeNo}, "
            + "callback_time = #{callbackTime} WHERE id = #{id} AND status = 0")
    int markFailed(@Param("id") Long id,
                   @Param("thirdTradeNo") String thirdTradeNo,
                   @Param("callbackTime") LocalDateTime callbackTime);
}
