package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.QwenAiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentAgent implements ReportAgent<SentimentAgent.Input, SentimentAgent.Output> {

    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT =
            "你是一位专业的舆情情感分析师。请对以下内容进行情感分类。\n" +
            "情感等级：非常正面(5)、正面(4)、中性(3)、负面(2)、非常负面(1)\n" +
            "**直接返回 JSON 数组**（顶层必须是 `[...]`，不要外层对象、不要 ```json 围栏、不要任何说明文字），每项包含 index(序号) 和 sentiment(情感等级名) 字段。\n" +
            "示例: [{\"index\":0,\"sentiment\":\"正面\"},{\"index\":1,\"sentiment\":\"负面\"}]";

    @Override
    public String name() {
        return "SENTIMENT_AGENT";
    }

    @Override
    public Output execute(Input input) throws AgentExecutionException {
        log.info("[SentimentAgent] 开始分析 {} 条内容", input.getContents().size());
        Map<Integer, String> results = new HashMap<>();
        List<String> contents = input.getContents();

        // 批量处理，每批50条
        int batchSize = 50;
        for (int i = 0; i < contents.size(); i += batchSize) {
            List<String> batch = contents.subList(i, Math.min(i + batchSize, contents.size()));
            StringBuilder userPrompt = new StringBuilder("请对以下内容进行情感分类：\n");
            for (int j = 0; j < batch.size(); j++) {
                String content = batch.get(j);
                if (content.length() > 200) {
                    content = content.substring(0, 200);
                }
                userPrompt.append(String.format("%d. %s\n", i + j, content));
            }

            try {
                String response = qwenAiService.chatWithScreeningModel(SYSTEM_PROMPT, userPrompt.toString());
                String cleaned = cleanJson(response);
                List<Map<String, Object>> parsed = extractItemList(cleaned);
                int batchPut = 0;
                for (Map<String, Object> item : parsed) {
                    Object idxObj = item.get("index");
                    Object sentObj = item.get("sentiment");
                    if (idxObj == null || sentObj == null) continue;
                    int idx = ((Number) idxObj).intValue();
                    // AI 可能返回相对 batch 的 index（0..49），也可能返回绝对（i..i+49）
                    if (idx < i) idx = i + idx;
                    String normalized = normalizeSentiment(String.valueOf(sentObj));
                    results.put(idx, normalized);
                    batchPut++;
                }
                log.info("[SentimentAgent] 批次 {}-{} 解析成功, 入库 {} 条", i, i + batch.size(), batchPut);
                // 该批次未返回的 index 兜底中性
                for (int j = 0; j < batch.size(); j++) {
                    results.putIfAbsent(i + j, "中性");
                }
            } catch (Exception e) {
                log.warn("[SentimentAgent] 批次 {}-{} 解析失败: {}", i, i + batch.size(), e.getMessage());
                for (int j = 0; j < batch.size(); j++) {
                    results.putIfAbsent(i + j, "中性");
                }
            }
        }

        return new Output(results);
    }

    @Override
    public Output fallback(Input input, Exception cause) {
        log.warn("[SentimentAgent] 降级: {}", cause.getMessage());
        Map<Integer, String> results = new HashMap<>();
        for (int i = 0; i < input.getContents().size(); i++) {
            results.put(i, "中性");
        }
        return new Output(results);
    }

    private String cleanJson(String raw) {
        raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        raw = raw.replaceAll(",\\s*([}\\]])", "$1");
        return raw;
    }

    /** 兼容 LLM 返回 [...] 或 {"key":[...]} 两种格式 */
    private List<Map<String, Object>> extractItemList(String cleaned) throws Exception {
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(cleaned);
        if (node.isArray()) {
            return objectMapper.convertValue(node,
                    new TypeReference<List<Map<String, Object>>>() {});
        }
        if (node.isObject()) {
            // 优先找已知字段名，再退化到第一个数组字段
            for (String key : new String[]{"results", "data", "sentiments", "items", "list"}) {
                com.fasterxml.jackson.databind.JsonNode v = node.get(key);
                if (v != null && v.isArray()) {
                    return objectMapper.convertValue(v,
                            new TypeReference<List<Map<String, Object>>>() {});
                }
            }
            java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = node.elements();
            while (it.hasNext()) {
                com.fasterxml.jackson.databind.JsonNode v = it.next();
                if (v.isArray()) {
                    return objectMapper.convertValue(v,
                            new TypeReference<List<Map<String, Object>>>() {});
                }
            }
        }
        throw new IllegalArgumentException("无法从响应中提取数组: " + cleaned.substring(0, Math.min(120, cleaned.length())));
    }

    /** 规范化 AI 返回的 sentiment 字符串：去空格/标点，识别中英文别名 + 数字分级 */
    private String normalizeSentiment(String raw) {
        if (raw == null) return "中性";
        String s = raw.trim().replaceAll("[\\s\"'`()（）]+", "").toLowerCase();
        // 数字分级：5/4/3/2/1
        if (s.contains("5") || s.contains("verypositive") || s.contains("非常正面") || s.contains("非常正向")) return "非常正面";
        if (s.contains("1") || s.contains("verynegative") || s.contains("非常负面") || s.contains("非常负向")) return "非常负面";
        if (s.contains("4") || s.contains("positive") || s.contains("正面") || s.contains("正向") || s.contains("积极")) return "正面";
        if (s.contains("2") || s.contains("negative") || s.contains("负面") || s.contains("负向") || s.contains("消极")) return "负面";
        if (s.contains("3") || s.contains("neutral") || s.contains("中性") || s.contains("中立")) return "中性";
        return "中性";
    }

    @Data
    public static class Input {
        private List<String> contents;

        public Input(List<String> contents) {
            this.contents = contents;
        }
    }

    @Data
    public static class Output {
        private Map<Integer, String> sentimentMap;

        public Output(Map<Integer, String> sentimentMap) {
            this.sentimentMap = sentimentMap;
        }
    }
}
