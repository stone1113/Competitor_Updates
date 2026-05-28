package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.DailyReportRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface DailyReportRecordMapper extends BaseMapper<DailyReportRecord> {

    @Select("SELECT * FROM daily_report_record WHERE brand_name = #{brand} AND report_date = #{date}")
    DailyReportRecord findByBrandAndDate(@Param("brand") String brand, @Param("date") LocalDate date);

    /** 取最近 N 天的日报记录，按日期降序 */
    @Select("SELECT * FROM daily_report_record WHERE brand_name = #{brand} " +
            "AND report_date <= #{endDate} " +
            "ORDER BY report_date DESC LIMIT #{limit}")
    java.util.List<DailyReportRecord> findRecentByBrand(
            @Param("brand") String brand,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit);
}
