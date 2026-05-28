package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTextSections {

    private List<CoreNewsItem> coreNews;
    private List<HotDiscussion> hotDiscussions;
    private Map<String, CompetitorTracking> competitorTracking;
    private List<IndustryHotspot> industryHotspots;
    private RiskAssessment riskAssessment;
    private List<CompetitorBenchmark> competitorBenchmark;
    private List<SuggestedTalkingPoint> suggestedTalkingPoints;
    private String dailyBriefing;
    private List<String> crisisAlerts;
    private List<TopicCluster> topics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicCluster {
        private String name;
        private Integer count;
        private String sentimentTag;
        private String exampleTitle;
        private String exampleUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreNewsItem {
        private String tag;
        private String sentiment;
        private String title;
        private String analysis;
        private String sourceUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HotDiscussion {
        private String sentimentTag;
        private String platform;
        private String author;
        private String title;
        private String stats;
        private String comment;
        private String sourceUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorTracking {
        private String action;
        private String sentiment;
        private String marketing;
        private String sourceUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustryHotspot {
        private String type;
        private String content;
        private String sourceUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private String red;
        private String yellow;
        private String green;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorBenchmark {
        private String mengshiModel;
        private String competitorModel;
        private String benchmarkType;
        private String topic;
        private String competitorClaim;
        private String mengshiAdvantage;
        private String verdict;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestedTalkingPoint {
        private String mengshiModel;
        private String competitorModel;
        private String scenario;
        private String talkingPoint;
        private String techReference;
        /** 人群编号，如 "P01"。竞品分析日报用，舆情日报可留空。 */
        private String persona;
        /** 人群名称，如 "硬派越野玩家"。冗余字段方便卡片直接展示。 */
        private String personaName;
    }
}
