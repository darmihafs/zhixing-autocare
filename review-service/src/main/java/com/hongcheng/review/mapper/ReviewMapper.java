package com.hongcheng.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hongcheng.review.dto.ScoreCount;
import com.hongcheng.review.entity.Review;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评价 Mapper：列表走 MyBatis-Plus 分页，聚合统计走显式 SQL。
 */
public interface ReviewMapper extends BaseMapper<Review> {

    /** 可见评价总数 */
    @Select("SELECT COUNT(*) FROM t_review WHERE item_type = #{itemType} AND item_id = #{itemId} "
            + "AND status = 1 AND deleted = 0")
    long countVisible(@Param("itemType") Integer itemType, @Param("itemId") Long itemId);

    /** 平均分（保留 1 位小数），无评价时 0 */
    @Select("SELECT COALESCE(ROUND(AVG(score), 1), 0) FROM t_review "
            + "WHERE item_type = #{itemType} AND item_id = #{itemId} AND status = 1 AND deleted = 0")
    BigDecimal avgScore(@Param("itemType") Integer itemType, @Param("itemId") Long itemId);

    /** 点赞数原子增减（GREATEST 兜底不为负；消费端已做事件去重） */
    @Update("UPDATE t_review SET like_count = GREATEST(like_count + #{delta}, 0) "
            + "WHERE id = #{id} AND deleted = 0")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /** 各星级数量（仅返回有数据的星级） */
    @Select("SELECT score, COUNT(*) AS cnt FROM t_review "
            + "WHERE item_type = #{itemType} AND item_id = #{itemId} AND status = 1 AND deleted = 0 "
            + "GROUP BY score")
    List<ScoreCount> countByScore(@Param("itemType") Integer itemType, @Param("itemId") Long itemId);

    /** 门店可见评价总数 */
    @Select("SELECT COUNT(*) FROM t_review WHERE shop_id = #{shopId} AND status = 1 AND deleted = 0")
    long countVisibleByShop(@Param("shopId") Long shopId);

    /** 门店平均分（保留 1 位小数），无评价时 0 */
    @Select("SELECT COALESCE(ROUND(AVG(score), 1), 0) FROM t_review "
            + "WHERE shop_id = #{shopId} AND status = 1 AND deleted = 0")
    BigDecimal avgScoreByShop(@Param("shopId") Long shopId);

    /** 门店各星级数量（仅返回有数据的星级） */
    @Select("SELECT score, COUNT(*) AS cnt FROM t_review "
            + "WHERE shop_id = #{shopId} AND status = 1 AND deleted = 0 GROUP BY score")
    List<ScoreCount> countByScoreForShop(@Param("shopId") Long shopId);
}
