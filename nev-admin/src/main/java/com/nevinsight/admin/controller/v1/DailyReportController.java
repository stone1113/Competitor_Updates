package com.nevinsight.admin.controller.v1;

import com.nevinsight.collector.service.PlatformQueryService;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.dto.response.*;
import com.nevinsight.model.mapper.core.DailySentimentSummaryMapper;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import com.nevinsight.model.entity.core.DailySentimentSummary;
import com.nevinsight.model.entity.core.WebSearchNews;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/daily-report")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailySentimentSummaryMapper summaryMapper;
    private final WebSearchNewsMapper webSearchNewsMapper;
    private final PlatformQueryService platformQueryService;

    @GetMapping("/kpi")
    public ApiResponse<DailyReportKpiResponse> getKpi(
            @RequestParam(defaultValue = "猛士") String brand,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailySentimentSummary s = summaryMapper.findByBrandAndDate(brand, date);
        if (s == null) {
            return ApiResponse.success(null);
        }

        int total = (s.getTotalMentions() != null ? s.getTotalMentions() : 0);
        double negRatio = s.getNegativeRatio() != null ? s.getNegativeRatio() : 0;
        String statusLevel = negRatio > 0.25 ? "red" : negRatio > 0.15 ? "yellow" : "green";

        return ApiResponse.success(DailyReportKpiResponse.builder()
                .brandName(s.getBrandName())
                .reportDate(s.getReportDate().toString())
                .statusLevel(statusLevel)
                .totalCoverage(total)
                .positiveRatio(1 - negRatio - 0.5)
                .negativeRatio(negRatio)
                .avgSentimentScore(s.getAvgSentimentScore() != null ? s.getAvgSentimentScore() : 0)
                .totalEngagement(s.getTotalEngagement() != null ? s.getTotalEngagement() : 0)
                .riskAlertCount(s.getRiskAlertCount() != null ? s.getRiskAlertCount() : 0)
                .veryPositiveCount(s.getVeryPositiveCount() != null ? s.getVeryPositiveCount() : 0)
                .positiveCount(s.getPositiveCount() != null ? s.getPositiveCount() : 0)
                .neutralCount(s.getNeutralCount() != null ? s.getNeutralCount() : 0)
                .negativeCount(s.getNegativeCount() != null ? s.getNegativeCount() : 0)
                .veryNegativeCount(s.getVeryNegativeCount() != null ? s.getVeryNegativeCount() : 0)
                .build());
    }

    @GetMapping("/top-news")
    public ApiResponse<List<TopNewsItemResponse>> getTopNews(
            @RequestParam(defaultValue = "猛士") String brand,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "10") int limit) {
        List<WebSearchNews> news = webSearchNewsMapper.findTopByBrandAndDate(brand, date, limit);
        List<TopNewsItemResponse> result = news.stream()
                .map(n -> TopNewsItemResponse.builder()
                        .title(n.getTitle())
                        .url(n.getUrl())
                        .content(n.getContent())
                        .brandName(n.getBrandName())
                        .sourceTool(n.getSourceTool())
                        .publishedDate(n.getPublishedDate())
                        .relevanceScore(n.getRelevanceScore())
                        .searchQuery(n.getSearchQuery())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @GetMapping("/top-buzz")
    public ApiResponse<List<BuzzItemResponse>> getTopBuzz(
            @RequestParam(defaultValue = "猛士") String brand,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(platformQueryService.queryBuzzUnion(brand, date, null, limit, 0));
    }

    @GetMapping("/buzz")
    public ApiResponse<List<BuzzItemResponse>> getBuzzPaged(
            @RequestParam(defaultValue = "猛士") String brand,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(platformQueryService.queryBuzzUnion(brand, date, platform, size, page * size));
    }
}
