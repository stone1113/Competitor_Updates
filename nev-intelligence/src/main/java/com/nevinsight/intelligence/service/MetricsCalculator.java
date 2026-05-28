package com.nevinsight.intelligence.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MetricsCalculator {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyKPI {
        private double positiveRatio;
        private double negativeRatio;
        private double avgSentimentScore;
        private int totalMentions;
        private long totalEngagement;
        private double mentionsChangePct;
        private String statusLevel;
        private int riskAlertCount;
        private int veryPositiveCount;
        private int positiveCount;
        private int neutralCount;
        private int negativeCount;
        private int veryNegativeCount;
    }

    /**
     * 计算情感健康度 (1-5 分)
     */
    public double computeAvgSentimentScore(int veryPositive, int positive, int neutral,
                                           int negative, int veryNegative) {
        int total = veryPositive + positive + neutral + negative + veryNegative;
        if (total == 0) return 3.0;
        return (veryPositive * 5.0 + positive * 4.0 + neutral * 3.0 +
                negative * 2.0 + veryNegative * 1.0) / total;
    }

    /**
     * 判定态势等级
     */
    public String determineStatusLevel(double negativeRatio, int riskAlertCount,
                                       double redThreshold, double yellowThreshold) {
        if (negativeRatio > redThreshold || riskAlertCount >= 3) {
            return "red";
        } else if (negativeRatio > yellowThreshold || riskAlertCount > 0) {
            return "yellow";
        }
        return "green";
    }

    /**
     * Z-Score 负面异常检测
     */
    public boolean detectNegativeAnomaly(List<Double> historicalNegativeRatios, double currentRatio) {
        if (historicalNegativeRatios.size() < 3) return false;
        double mean = historicalNegativeRatios.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = historicalNegativeRatios.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        if (stdDev == 0) return false;
        double zScore = (currentRatio - mean) / stdDev;
        return zScore > 2.0;
    }

    /**
     * 计算情感标签对应的数值分数
     */
    public static int sentimentToScore(String sentiment) {
        if (sentiment == null) return 3;
        switch (sentiment) {
            case "非常正面": return 5;
            case "正面": return 4;
            case "中性": return 3;
            case "负面": return 2;
            case "非常负面": return 1;
            default: return 3;
        }
    }
}
