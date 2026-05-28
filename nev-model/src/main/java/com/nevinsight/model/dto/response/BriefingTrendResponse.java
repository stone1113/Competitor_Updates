package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BriefingTrendResponse {
    private List<TrendPoint> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String date;
        private int totalMentions;
        private long totalEngagement;
        private double avgSentimentScore;
        private double negativeRatio;
        private double positiveRatio;
        private int riskAlertCount;
    }
}
