package com.nevinsight.model.mapper.core;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nevinsight.model.entity.core.GasgooSalesRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GasgooSalesRecordMapper extends BaseMapper<GasgooSalesRecord> {

    /** UPSERT 单条记录（unique (gasgoo_series_id, period_year, period_month)） */
    @org.apache.ibatis.annotations.Insert(
        "INSERT INTO gasgoo_sales_record " +
        "  (brand_name, model_name, gasgoo_series_id, period_year, period_month, " +
        "   sales_count, brand_total, segment_total, ytd_count, source_url, crawl_ts, add_ts, last_modify_ts) " +
        "VALUES (#{brandName}, #{modelName}, #{gasgooSeriesId}, #{periodYear}, #{periodMonth}, " +
        "        #{salesCount}, #{brandTotal}, #{segmentTotal}, #{ytdCount}, #{sourceUrl}, #{crawlTs}, " +
        "        UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000) " +
        "ON DUPLICATE KEY UPDATE " +
        "  brand_name=VALUES(brand_name), model_name=VALUES(model_name), " +
        "  sales_count=VALUES(sales_count), brand_total=VALUES(brand_total), " +
        "  segment_total=VALUES(segment_total), ytd_count=VALUES(ytd_count), " +
        "  source_url=VALUES(source_url), crawl_ts=VALUES(crawl_ts), " +
        "  last_modify_ts=UNIX_TIMESTAMP()*1000")
    int upsert(GasgooSalesRecord record);

    /** 每品牌最新一条销量记录（按 year DESC, month DESC）— 销量板块用。 */
    @Select("SELECT * FROM (" +
            "  SELECT *, ROW_NUMBER() OVER (PARTITION BY model_name ORDER BY period_year DESC, period_month DESC) AS rn " +
            "  FROM gasgoo_sales_record " +
            ") t WHERE rn = 1 ORDER BY brand_name, model_name")
    List<GasgooSalesRecord> findLatestByModel();

    /** 单车型某月销量（用于补漏判断） */
    @Select("SELECT * FROM gasgoo_sales_record " +
            "WHERE gasgoo_series_id=#{gasgooSeriesId} AND period_year=#{year} AND period_month=#{month}")
    GasgooSalesRecord findOne(@Param("gasgooSeriesId") String gasgooSeriesId,
                              @Param("year") int year,
                              @Param("month") int month);
}
