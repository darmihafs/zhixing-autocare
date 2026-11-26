package com.hongcheng.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongcheng.product.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品表 Mapper。
 * <p>
 * 防超卖核心：扣减为单条原子 UPDATE，stock >= n 条件在数据库行锁内判定，
 * 并发下不可能扣成负数；影响行数 0 即库存不足，由服务层转业务码并回滚整单。
 */
public interface ProductMapper extends BaseMapper<Product> {

    /** 扣库存并加销量；返回影响行数（0=库存不足/下架/已删） */
    @Update("UPDATE t_product SET stock = stock - #{quantity}, sales = sales + #{quantity} "
            + "WHERE id = #{productId} AND deleted = 0 AND status = 1 AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /** 回补库存并减销量（订单取消联动）；sales >= n 防止销量扣成负数 */
    @Update("UPDATE t_product SET stock = stock + #{quantity}, sales = sales - #{quantity} "
            + "WHERE id = #{productId} AND deleted = 0 AND sales >= #{quantity}")
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}