package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.QwenAiService;
import com.nevinsight.model.dto.response.CardTextSections;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 生成"今日舆情速览"+"议题聚类"。一次 LLM 调用同时返回两块。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BriefingAgent implements ReportAgent<BriefingAgent.Input, BriefingAgent.Output> {

    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT =
            "你是一位专业的汽车品牌舆情分析师。基于当日 KPI、7 日均值和热议帖列表，输出 JSON：\n" +
            "{\n" +
            "  \"briefing\": \"2-3 句中文，必须包含：(1) 今日声量/健康度方向（同比 7 日均值的升/降），(2) 主导话题或情绪触发因素，(3) 一句给到管理层的判断\",\n" +
            "  \"topics\": [3-5 个议题，从热议帖中聚类。每个: {name(2-4字议题名,如\\\"智驾\\\"\\\"越野\\\"\\\"价格\\\"\\\"质量\\\"\\\"售后\\\"), count(整数,该议题相关帖数估计), sentimentTag(非常正面|正面|中性|负面|非常负面 中之一,议题整体情感), exampleIndex(整数,最具代表性的帖在输入数组中的下标)}]\n" +
            "}\n" +
            "仅返回 JSON，不要任何说明文字、Markdown 围栏或注释。";

    @Override
    public String name() {
        return "BRIEFING_AGENT";
    }

    @Override
    public Output execute(Input input) throws AgentExecutionException {
        log.info("[BriefingAgent] 生成速览+议题聚类, 帖子数={}", input.getPosts().size());
        try {
            String userPrompt = buildPrompt(input);
            String resp = qwenAiService.chatWithReportModel(SYSTEM_PROMPT, userPrompt);
            String cleaned = cleanJson(resp);
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
            String briefing = String.valueOf(parsed.getOrDefault("briefing", ""));

            List<CardTextSections.TopicCluster> topics = new ArrayList<>();
            Object rawTopics = parsed.get("topics");
            if (rawTopics instanceof List) {
                for (Object item : (List<?>) rawTopics) {
                    if (!(item instanceof Map)) continue;
                    Map<?, ?> m = (Map<?, ?>) item;
                    Object nameObj = m.get("name");
                    String name = nameObj == null ? "" : String.valueOf(nameObj);
                    Integer count = toInt(m.get("count"));
                    Object sentObj = m.get("sentimentTag");
                    String sentimentTag = sentObj == null ? "中性" : String.valueOf(sentObj);
                    Integer idx = toInt(m.get("exampleIndex"));
                    String exampleTitle = "";
                    String exampleUrl = "";
                    if (idx != null && idx >= 0 && idx < input.getPosts().size()) {
                        PostRef p = input.getPosts().get(idx);
                        exampleTitle = p.getTitle();
                        exampleUrl = p.getUrl();
                    }
                    topics.add(CardTextSections.TopicCluster.builder()
                            .name(name)
                            .count(count == null ? 0 : count)
                            .sentimentTag(sentimentTag)
                            .exampleTitle(exampleTitle)
                            .exampleUrl(exampleUrl)
                            .build());
                }
            }
            return new Output(briefing, topics);
        } catch (Exception e) {
            log.error("[BriefingAgent] 执行失败: {}", e.getMessage());
            throw new AgentExecutionException("速览/议题生成失败", e);
        }
    }

    @Override
    public Output fallback(Input input, Exception cause) {
        log.warn("[BriefingAgent] 降级: {}", cause == null ? "" : cause.getMessage());
        return new Output("", Collections.emptyList());
    }

    private String buildPrompt(Input in) {
        StringBuilder sb = new StringBuilder();
        sb.append("品牌：").append(in.getBrand()).append("\n");
        sb.append("今日 KPI：总声量=").append(in.getTodayTotal())
          .append(", 正面率=").append(pct(in.getTodayPosRatio()))
          .append(", 负面率=").append(pct(in.getTodayNegRatio()))
          .append(", 健康度=").append(round(in.getTodayHealth(), 2)).append("\n");
        sb.append("7 日均值：总声量=").append(round(in.getAvg7Total(), 0))
          .append(", 正面率=").append(pct(in.getAvg7PosRatio()))
          .append(", 负面率=").append(pct(in.getAvg7NegRatio()))
          .append(", 健康度=").append(round(in.getAvg7Health(), 2)).append("\n");
        sb.append("热议帖列表（下标从 0 开始）：\n");
        for (int i = 0; i < in.getPosts().size(); i++) {
            PostRef p = in.getPosts().get(i);
            String t = p.getTitle() == null ? "" : p.getTitle();
            if (t.length() > 80) t = t.substring(0, 80);
            String c = p.getContent() == null ? "" : p.getContent();
            if (c.length() > 120) c = c.substring(0, 120);
            sb.append(i).append(". [").append(p.getPlatform()).append("] ")
              .append("情感=").append(p.getSentimentTag()).append(" 互动=").append(p.getEngagement())
              .append("\n   标题：").append(t)
              .append("\n   正文：").append(c).append("\n");
        }
        return sb.toString();
    }

    private static String pct(Double v) {
        if (v == null) return "0%";
        return String.format("%.1f%%", v * 100);
    }

    private static String round(Double v, int decimals) {
        if (v == null) return "0";
        return String.format("%." + decimals + "f", v);
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{}";
        raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        raw = raw.replaceAll(",\\s*([}\\]])", "$1");
        return raw;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostRef {
        private String platform;
        private String title;
        private String content;
        private String url;
        private String sentimentTag;
        private long engagement;
    }

    @Data
    public static class Input {
        private String brand;
        private int todayTotal;
        private double todayPosRatio;
        private double todayNegRatio;
        private double todayHealth;
        private double avg7Total;
        private double avg7PosRatio;
        private double avg7NegRatio;
        private double avg7Health;
        private List<PostRef> posts;
    }

    @Data
    @AllArgsConstructor
    public static class Output {
        private String briefing;
        private List<CardTextSections.TopicCluster> topics;
    }
}
