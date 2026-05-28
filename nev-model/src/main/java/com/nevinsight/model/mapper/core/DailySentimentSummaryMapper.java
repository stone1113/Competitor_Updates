package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.DailySentimentSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailySentimentSummaryMapper extends BaseMapper<DailySentimentSummary> {

    @Select("SELECT * FROM daily_sentiment_summary WHERE brand_name = #{brand} AND report_date = #{date}")
    DailySentimentSummary findByBrandAndDate(@Param("brand") String brand, @Param("date") LocalDate date);

    @Select("SELECT * FROM daily_sentiment_summary WHERE brand_name = #{brand} " +
            "AND report_date BETWEEN #{startDate} AND #{endDate} ORDER BY report_date ASC")
    List<DailySentimentSummary> findByBrandAndDateRange(
            @Param("brand") String brand,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Select("SELECT * FROM daily_sentiment_summary WHERE brand_name = #{brand} " +
            "ORDER BY report_date DESC LIMIT #{limit}")
    List<DailySentimentSummary> findLatestByBrand(@Param("brand") String brand, @Param("limit") int limit);
}
