package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyReportKpiResponse {

    private String brandName;
    private String reportDate;
    private String statusLevel;
    private int totalCoverage;
    private int socialMentions;
    private int webMentions;
    private double coverageChangePct;
    private double positiveRatio;
    private double positiveRatioChange;
    private double negativeRatio;
    private double avgSentimentScore;
    private long totalEngagement;
    private double engagementChangePct;
    private int riskAlertCount;
    private int veryPositiveCount;
    private int positiveCount;
    private int neutralCount;
    private int negativeCount;
    private int veryNegativeCount;
    private String statusSummary;
}
