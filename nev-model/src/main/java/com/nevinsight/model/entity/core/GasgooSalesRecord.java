package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 盖世汽车车型月销量记录（结构化权威源）。
 * 一行 = 一个车型某个 (year, month) 的销量，含厂商总销量和同级别总销量做对照。
 *
 * 数据来源：https://m.gasgoo.com/qcxl/cxxl/{year}/{month}/{gasgoo_series_id}
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gasgoo_sales_record")
public class GasgooSalesRecord extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("model_name")
    private String modelName;

    @TableField("gasgoo_series_id")
    private String gasgooSeriesId;

    @TableField("period_year")
    private Integer periodYear;

    @TableField("period_month")
    private Integer periodMonth;

    /** 该车型该月销量（辆） */
    @TableField("sales_count")
    private Integer salesCount;

    /** 同厂商当月总销量（如「比亚迪汽车」314100） */
    @TableField("brand_total")
    private Integer brandTotal;

    /** 同级别（如「D级」「中型SUV」）当月总销量 */
    @TableField("segment_total")
    private Integer segmentTotal;

    /** 本年累计销量 */
    @TableField("ytd_count")
    private Integer ytdCount;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("crawl_ts")
    private Long crawlTs;
}
