package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 竞品官方账号配置（微博 / 抖音 / 小红书）。
 *
 * 每条记录 → 每天定时拉一次该账号最新 N 条发帖，
 * 经 OfficialPostIngester 转写到 web_search_news，复用 PR11 事件分类管线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("official_account_config")
public class OfficialAccountConfig extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    /** wb | dy | xhs */
    @TableField("platform")
    private String platform;

    /** 平台 UID（微博）/ sec_uid（抖音）/ profile id（小红书） */
    @TableField("account_id")
    private String accountId;

    @TableField("account_name")
    private String accountName;

    @TableField("account_url")
    private String accountUrl;

    @TableField("is_enabled")
    private Boolean isEnabled;

    /** v7：权威销量数据源（乘联会/中汽协/车企官号），仅影响竞品日报销量板块过滤 */
    @TableField("is_authoritative_sales")
    private Boolean isAuthoritativeSales;

    @TableField("last_crawl_ts")
    private Long lastCrawlTs;

    @TableField("remark")
    private String remark;
}
