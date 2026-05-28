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
public class CompetitorKpiResponse {
    private String reportDate;
    private List<BrandKpi> brands;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandKpi {
        private String brandName;
        private int totalCoverage;
        private double positiveRatio;
        private long totalEngagement;
        private double avgSentimentScore;
        private double negativeRatio;
    }
}
