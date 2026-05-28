package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.AutohomeSpec;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AutohomeSpecMapper extends BaseMapper<AutohomeSpec> {

    /** 查指定 series_id 在最近一次抓取日期下的全部参数（用于拼对标表）。 */
    @Select("SELECT * FROM autohome_spec " +
            "WHERE series_id = #{seriesId} " +
            "  AND crawl_date = (SELECT MAX(crawl_date) FROM autohome_spec WHERE series_id = #{seriesId}) " +
            "ORDER BY param_category, param_name")
    List<AutohomeSpec> findLatestBySeriesId(@Param("seriesId") String seriesId);

    /** 查指定 series_id 的最后抓取日期 + 参数行数。 */
    @Select("SELECT COALESCE(MAX(crawl_date), NULL) FROM autohome_spec WHERE series_id = #{seriesId}")
    java.time.LocalDate findLastCrawlDate(@Param("seriesId") String seriesId);

    @Select("SELECT COUNT(*) FROM autohome_spec WHERE series_id = #{seriesId} " +
            "  AND crawl_date = (SELECT MAX(crawl_date) FROM autohome_spec WHERE series_id = #{seriesId})")
    int countLatestBySeriesId(@Param("seriesId") String seriesId);

    /** UPSERT — 同 (spec_id, param_name, crawl_date) 重跑覆盖。 */
    @org.apache.ibatis.annotations.Insert(
            "INSERT INTO autohome_spec (series_id, spec_id, spec_name, param_category, param_name, param_value, crawl_date, add_ts) " +
            "VALUES (#{seriesId}, #{specId}, #{specName}, #{paramCategory}, #{paramName}, #{paramValue}, #{crawlDate}, #{addTs}) " +
            "ON DUPLICATE KEY UPDATE " +
            "  spec_name = VALUES(spec_name), " +
            "  param_category = VALUES(param_category), " +
            "  param_value = VALUES(param_value)")
    int upsert(AutohomeSpec spec);
}
