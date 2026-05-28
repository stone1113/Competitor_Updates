package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BriefingOverviewResponse {
    private String brandName;
    private String reportDate;
    private String statusLevel;
    private int totalMentions;
    private long totalEngagement;
    private double positiveRatio;
    private double negativeRatio;
    private double avgSentimentScore;
    private int riskAlertCount;
    private double mentionsChangePct;
    private double sentimentChange;
    private int veryPositiveCount;
    private int positiveCount;
    private int neutralCount;
    private int negativeCount;
    private int veryNegativeCount;
    private Map<String, Object> platformBreakdown;
}
