package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.QwenAiService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardGroupSummaryAgent {

    private static final int BATCH_SIZE = 4;
    private static final int TEXT_LIMIT = 900;

    private static final String SYSTEM_PROMPT =
            "你是猛士车型品牌的市场分析员。口径说明：猛士是车型品牌/产品品牌，M817、M917 是猛士旗下具体车型。"
            + "你的任务是基于飞书卡片中已经确定展示的明细，"
            + "为每个分组写一句市场分析总结。\n"
            + "只能基于输入明细，不得编造，不得总结未展示内容。\n"
            + "输出必须是 JSON 数组，每个对象字段：group_key, summary, confidence, hide_reason。\n"
            + "summary 为一句中文，建议 60-100 字；没有价值或无法覆盖明细时 summary=\"\"。\n"
            + "\n"
            + "硬性要求：\n"
            + "- 总结必须覆盖输入中实际展示的主要明细；多条明细主题不同，也要同时覆盖。\n"
            + "- 禁止复述标题，禁止照搬 OCR，禁止编造输入外信息。\n"
            + "- 禁止模板化表达：形成传播触点、重点释放、持续传播、多维度升级、展开传播。\n"
            + "- 不要输出“总结：”前缀。\n"
            + "- 视觉高亮：如果总结中出现重要技术/配置/平台名、具体参数、权益金额/金融周期、订单/销量数字、赛事成绩/活动时间地点，"
            + "请用 Markdown **加粗** 1-3 个事实锚点；禁止加粗市场份额、竞争压力、品牌宣传、值得关注等抽象判断词。\n"
            + "\n"
            + "分板块要求：\n"
            + "launch 产品动态：概括配置/技术/参数/场景变化，并说明对猛士的竞争意义。\n"
            + "price_finance 价格金融：概括权益结构、补贴/金融组合及终端转化影响。\n"
            + "campaign 营销传播：概括赛事、车展、主题片、试驾、用户互动等传播动作组合，并说明品牌传播价值。\n"
            + "strategic_action 战略动作：只概括品牌级/公司级/产业链级长期动作，例如技术路线、产品矩阵/路线、供应链合作、产能、出海、组织资本动作；"
            + "不要把普通车型上市、预售、亮相、配置升级总结成战略动作。";

    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    public Map<String, CardGroupSummary> generate(List<GroupSummaryInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return Collections.emptyMap();
        LinkedHashMap<String, CardGroupSummary> out = new LinkedHashMap<>();
        int failed = 0;
        for (int i = 0; i < inputs.size(); i += BATCH_SIZE) {
            List<GroupSummaryInput> batch = inputs.subList(i, Math.min(i + BATCH_SIZE, inputs.size()));
            Map<String, CardGroupSummary> batchResult = generateBatch(batch);
            if (batchResult == null) {
                for (GroupSummaryInput input : batch) {
                    Map<String, CardGroupSummary> single = generateBatch(Collections.singletonList(input));
                    if (single == null) {
                        failed++;
                    } else {
                        out.putAll(single);
                    }
                }
            } else {
                out.putAll(batchResult);
                for (GroupSummaryInput input : batch) {
                    if (input != null && input.getGroupKey() != null && !batchResult.containsKey(input.getGroupKey())) {
                        Map<String, CardGroupSummary> single = generateBatch(Collections.singletonList(input));
                        if (single == null) {
                            failed++;
                        } else {
                            out.putAll(single);
                        }
                    }
                }
            }
        }
        long success = out.values().stream()
                .filter(x -> x.getSummary() != null && !x.getSummary().isEmpty())
                .count();
        long empty = out.values().stream()
                .filter(x -> x.getSummary() == null || x.getSummary().isEmpty())
                .count();
        log.info("[CardGroupSummaryAgent] group_summary_inputs={} llm_success={} llm_empty={} llm_failed={}",
                inputs.size(), success, empty, failed);
        return out;
    }

    private Map<String, CardGroupSummary> generateBatch(List<GroupSummaryInput> batch) {
        String response;
        try {
            response = qwenAiService.chatWithReportModelHttp(SYSTEM_PROMPT, buildPrompt(batch));
        } catch (Exception e) {
            log.warn("[CardGroupSummaryAgent] LLM failed batch size={}: {}", batch.size(), e.getMessage());
            return null;
        }
        return parseResponse(response);
    }

    private String buildPrompt(List<GroupSummaryInput> batch) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < batch.size(); i++) {
            GroupSummaryInput input = batch.get(i);
            sb.append("{")
              .append("\"group_key\":").append(jsonStr(input.getGroupKey())).append(',')
              .append("\"section\":").append(jsonStr(input.getSection())).append(',')
              .append("\"group_name\":").append(jsonStr(input.getGroupName())).append(',')
              .append("\"items\":[");
            List<GroupSummaryItem> items = input.getItems() == null ? Collections.emptyList() : input.getItems();
            for (int j = 0; j < items.size(); j++) {
                GroupSummaryItem item = items.get(j);
                sb.append("{")
                  .append("\"title\":").append(jsonStr(item.getTitle())).append(',')
                  .append("\"source\":").append(jsonStr(item.getSource())).append(',')
                  .append("\"points\":").append(jsonArray(item.getPoints())).append(',')
                  .append("\"ocr_detail\":").append(jsonStr(limit(item.getOcrDetail(), TEXT_LIMIT)))
                  .append("}");
                if (j < items.size() - 1) sb.append(',');
            }
            sb.append("]}");
            if (i < batch.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("]\n\n请返回纯 JSON 数组，与输入 group_key 对齐。");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, CardGroupSummary> parseResponse(String response) {
        if (response == null || response.trim().isEmpty()) return null;
        String cleaned = response.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
        int first = cleaned.indexOf('[');
        int last = cleaned.lastIndexOf(']');
        if (first >= 0 && last > first) {
            cleaned = cleaned.substring(first, last + 1);
        }
        try {
            List<Map<String, Object>> arr = objectMapper.readValue(cleaned, List.class);
            LinkedHashMap<String, CardGroupSummary> out = new LinkedHashMap<>();
            for (Map<String, Object> item : arr) {
                String key = toStr(item.get("group_key"));
                if (key.isEmpty()) continue;
                String summary = cleanSummary(toStr(item.get("summary")));
                CardGroupSummary value = new CardGroupSummary();
                value.setGroupKey(key);
                value.setSummary(summary);
                value.setConfidence(toDouble(item.get("confidence"), 0.0d));
                value.setHideReason(toStr(item.get("hide_reason")));
                out.put(key, value);
            }
            return out;
        } catch (Exception e) {
            log.warn("[CardGroupSummaryAgent] parse failed: {}", e.getMessage());
            return null;
        }
    }

    private static String cleanSummary(String summary) {
        String v = summary == null ? "" : summary.replaceAll("\\s+", " ").trim();
        v = v.replaceAll("^(总结|摘要|要点)[:：]\\s*", "").trim();
        if (v.length() < 18) return "";
        String compact = v.replaceAll("\\s+", "");
        if (compact.matches(".*(形成传播触点|重点释放|持续传播|多维度升级|展开传播).*")) return "";
        if (compact.matches(".*(官方账号|直播频道|免责声明|扫码|小程序).*")) return "";
        return v.length() > 120 ? v.substring(0, 120) : v;
    }

    private static String jsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            sb.append(jsonStr(limit(items.get(i), TEXT_LIMIT)));
            if (i < items.size() - 1) sb.append(',');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonStr(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim() + "\"";
    }

    private static String limit(String text, int max) {
        if (text == null) return "";
        String v = text.replaceAll("\\s+", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static Double toDouble(Object o, Double fallback) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble(((String) o).trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static String toStr(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    @Data
    public static class GroupSummaryInput {
        private final String groupKey;
        private final String section;
        private final String groupName;
        private final List<GroupSummaryItem> items;
    }

    @Data
    public static class GroupSummaryItem {
        private final Long id;
        private final String title;
        private final String source;
        private final List<String> points;
        private final String ocrDetail;
    }

    @Data
    public static class CardGroupSummary {
        private String groupKey;
        private String summary;
        private Double confidence;
        private String hideReason;
    }
}
