package com.nevinsight.admin.controller.v1;

import com.nevinsight.common.ApiResponse;
import com.nevinsight.common.PageResult;
import com.nevinsight.model.dto.response.BriefingOverviewResponse;
import com.nevinsight.model.dto.response.BriefingTrendResponse;
import com.nevinsight.model.entity.core.DailySentimentSummary;
import com.nevinsight.model.mapper.core.DailySentimentSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/briefing")
@RequiredArgsConstructor
public class DailyBriefingController {

    private final DailySentimentSummaryMapper summaryMapper;

    @GetMapping("/overview")
    public ApiResponse<BriefingOverviewResponse> getOverview(
            @RequestParam(defaultValue = "猛士") String brand) {
        DailySentimentSummary latest = summaryMapper.findLatestByBrand(brand, 1)
                .stream().findFirst().orElse(null);
        if (latest == null) {
            return ApiResponse.success(null);
        }

        int total = (latest.getVeryPositiveCount() != null ? latest.getVeryPositiveCount() : 0)
                + (latest.getPositiveCount() != null ? latest.getPositiveCount() : 0)
                + (latest.getNeutralCount() != null ? latest.getNeutralCount() : 0)
                + (latest.getNegativeCount() != null ? latest.getNegativeCount() : 0)
                + (latest.getVeryNegativeCount() != null ? latest.getVeryNegativeCount() : 0);
        double posRatio = total > 0 ? ((latest.getVeryPositiveCount() != null ? latest.getVeryPositiveCount() : 0)
                + (latest.getPositiveCount() != null ? latest.getPositiveCount() : 0)) * 1.0 / total : 0;

        String statusLevel = "green";
        double negRatio = latest.getNegativeRatio() != null ? latest.getNegativeRatio() : 0;
        if (negRatio > 0.25) statusLevel = "red";
        else if (negRatio > 0.15) statusLevel = "yellow";

        return ApiResponse.success(BriefingOverviewResponse.builder()
                .brandName(latest.getBrandName())
                .reportDate(latest.getReportDate().toString())
                .statusLevel(statusLevel)
                .totalMentions(latest.getTotalMentions() != null ? latest.getTotalMentions() : 0)
                .totalEngagement(latest.getTotalEngagement() != null ? latest.getTotalEngagement() : 0)
                .positiveRatio(posRatio)
                .negativeRatio(negRatio)
                .avgSentimentScore(latest.getAvgSentimentScore() != null ? latest.getAvgSentimentScore() : 0)
                .riskAlertCount(latest.getRiskAlertCount() != null ? latest.getRiskAlertCount() : 0)
                .veryPositiveCount(latest.getVeryPositiveCount() != null ? latest.getVeryPositiveCount() : 0)
                .positiveCount(latest.getPositiveCount() != null ? latest.getPositiveCount() : 0)
                .neutralCount(latest.getNeutralCount() != null ? latest.getNeutralCount() : 0)
                .negativeCount(latest.getNegativeCount() != null ? latest.getNegativeCount() : 0)
                .veryNegativeCount(latest.getVeryNegativeCount() != null ? latest.getVeryNegativeCount() : 0)
                .build());
    }

    @GetMapping("/trend")
    public ApiResponse<BriefingTrendResponse> getTrend(
            @RequestParam(defaultValue = "猛士") String brand,
            @RequestParam(defaultValue = "7") int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        List<DailySentimentSummary> summaries = summaryMapper.findByBrandAndDateRange(brand, start, end);

        List<BriefingTrendResponse.TrendPoint> points = summaries.stream()
                .map(s -> BriefingTrendResponse.TrendPoint.builder()
                        .date(s.getReportDate().toString())
                        .totalMentions(s.getTotalMentions() != null ? s.getTotalMentions() : 0)
                        .totalEngagement(s.getTotalEngagement() != null ? s.getTotalEngagement() : 0)
                        .avgSentimentScore(s.getAvgSentimentScore() != null ? s.getAvgSentimentScore() : 0)
                        .negativeRatio(s.getNegativeRatio() != null ? s.getNegativeRatio() : 0)
                        .build())
                .collect(Collectors.toList());

        return ApiResponse.success(BriefingTrendResponse.builder().points(points).build());
    }
}
