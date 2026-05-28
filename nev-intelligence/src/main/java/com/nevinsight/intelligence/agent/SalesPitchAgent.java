package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.config.BrandConfigProperties;
import com.nevinsight.intelligence.service.QwenAiService;
import com.nevinsight.model.dto.response.CardTextSections;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesPitchAgent implements ReportAgent<SalesPitchAgent.Input, SalesPitchAgent.Output> {

    private final QwenAiService qwenAiService;
    private final BrandConfigProperties brandConfig;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT =
            "你是一位精通新能源汽车行业的营销专家。请基于今日舆情数据生成销售话术建议。";

    @Override
    public String name() {
        return "PITCH_AGENT";
    }

    @Override
    public Output execute(Input input) throws AgentExecutionException {
        log.info("[SalesPitchAgent] 开始生成话术");

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("## 品牌: ").append(brandConfig.getName()).append("\n\n");
        promptBuilder.append("## 今日舆情概要\n").append(input.getSentimentSummary()).append("\n\n");
        promptBuilder.append("## 竞品动态\n").append(input.getCompetitorNews()).append("\n\n");
        promptBuilder.append("## 技术参考\n").append(input.getTechReference()).append("\n\n");
        promptBuilder.append("## 知识库参考\n").append(input.getKnowledgeContext()).append("\n\n");

        promptBuilder.append("请返回JSON数组 suggested_talking_points，3-5个对象，每个有:\n");
        promptBuilder.append("- mengshi_model: 猛士M817或猛士917\n");
        promptBuilder.append("- competitor_model: 对标竞品车型\n");
        promptBuilder.append("- scenario: 以\"当客户说……\"开头的场景\n");
        promptBuilder.append("- talking_point: 销售顾问应对话术\n");
        promptBuilder.append("- tech_reference: 引用的技术名称\n");
        promptBuilder.append("\n返回纯JSON，无markdown包裹。");

        try {
            String response = qwenAiService.chatWithReportModel(SYSTEM_PROMPT, promptBuilder.toString());
            response = cleanJson(response);

            // Handle both {"suggested_talking_points": [...]} and direct [...]
            List<CardTextSections.SuggestedTalkingPoint> points;
            if (response.trim().startsWith("[")) {
                points = objectMapper.readValue(response,
                        new TypeReference<List<CardTextSections.SuggestedTalkingPoint>>() {});
            } else {
                var parsed = objectMapper.readValue(response,
                        new TypeReference<java.util.Map<String, Object>>() {});
                points = objectMapper.convertValue(parsed.get("suggested_talking_points"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                CardTextSections.SuggestedTalkingPoint.class));
            }

            return new Output(points);
        } catch (Exception e) {
            log.error("[SalesPitchAgent] 执行失败: {}", e.getMessage());
            throw new AgentExecutionException("话术生成失败", e);
        }
    }

    @Override
    public Output fallback(Input input, Exception cause) {
        log.warn("[SalesPitchAgent] 降级: {}", cause.getMessage());
        return new Output(Collections.emptyList());
    }

    private String cleanJson(String raw) {
        raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        raw = raw.replaceAll(",\\s*([}\\]])", "$1");
        return raw;
    }

    @Data
    public static class Input {
        private String sentimentSummary;
        private String competitorNews;
        private String techReference;
        private String knowledgeContext;

        public Input(String sentimentSummary, String competitorNews, String techReference, String knowledgeContext) {
            this.sentimentSummary = sentimentSummary;
            this.competitorNews = competitorNews;
            this.techReference = techReference;
            this.knowledgeContext = knowledgeContext;
        }
    }

    @Data
    public static class Output {
        private List<CardTextSections.SuggestedTalkingPoint> talkingPoints;

        public Output(List<CardTextSections.SuggestedTalkingPoint> points) {
            this.talkingPoints = points;
        }
    }
}
