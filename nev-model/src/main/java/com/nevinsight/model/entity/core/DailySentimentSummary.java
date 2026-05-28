package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_sentiment_summary")
public class DailySentimentSummary extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("report_date")
    private LocalDate reportDate;

    @TableField("total_mentions")
    private Integer totalMentions;

    @TableField("total_engagement")
    private Long totalEngagement;

    @TableField("very_positive_count")
    private Integer veryPositiveCount;

    @TableField("positive_count")
    private Integer positiveCount;

    @TableField("neutral_count")
    private Integer neutralCount;

    @TableField("negative_count")
    private Integer negativeCount;

    @TableField("very_negative_count")
    private Integer veryNegativeCount;

    @TableField("avg_sentiment_score")
    private Double avgSentimentScore;

    @TableField("risk_alert_count")
    private Integer riskAlertCount;

    @TableField("negative_ratio")
    private Double negativeRatio;

    @TableField("platform_breakdown")
    private String platformBreakdown;
}
