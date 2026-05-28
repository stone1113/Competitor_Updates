package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.QwenAiService;
import com.nevinsight.model.dto.response.CardTextSections;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAssemblyAgent implements ReportAgent<ReportAssemblyAgent.Input, ReportAssemblyAgent.Output> {

    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT =
            "你是一位专业的汽车行业舆情分析师。请基于以下数据，生成飞书日报卡片的各板块文案。";

    @Override
    public String name() {
        return "ASSEMBLY_AGENT";
    }

    @Override
    public Output execute(Input input) throws AgentExecutionException {
        log.info("[ReportAssemblyAgent] 开始汇总日报");

        try {
            String response = qwenAiService.chatWithReportModel(SYSTEM_PROMPT, input.getFullPrompt());
            response = cleanJson(response);
            CardTextSections sections = objectMapper.readValue(response, CardTextSections.class);
            return new Output(sections);
        } catch (Exception e) {
            log.error("[ReportAssemblyAgent] 执行失败: {}", e.getMessage());
            throw new AgentExecutionException("日报汇总失败", e);
        }
    }

    @Override
    public Output fallback(Input input, Exception cause) {
        log.warn("[ReportAssemblyAgent] 降级: {}", cause.getMessage());
        CardTextSections sections = CardTextSections.builder()
                .coreNews(Collections.emptyList())
                .hotDiscussions(Collections.emptyList())
                .competitorTracking(Collections.emptyMap())
                .industryHotspots(Collections.emptyList())
                .riskAssessment(CardTextSections.RiskAssessment.builder()
                        .red("系统异常，无法生成")
                        .yellow("无")
                        .green("无")
                        .build())
                .competitorBenchmark(Collections.emptyList())
                .suggestedTalkingPoints(Collections.emptyList())
                .build();
        return new Output(sections);
    }

    private String cleanJson(String raw) {
        raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        raw = raw.replaceAll("//[^\\n]*", "");
        raw = raw.replaceAll(",\\s*([}\\]])", "$1");
        return raw;
    }

    @Data
    public static class Input {
        private String fullPrompt;

        public Input(String fullPrompt) {
            this.fullPrompt = fullPrompt;
        }
    }

    @Data
    public static class Output {
        private CardTextSections sections;

        public Output(CardTextSections sections) {
            this.sections = sections;
        }
    }
}
