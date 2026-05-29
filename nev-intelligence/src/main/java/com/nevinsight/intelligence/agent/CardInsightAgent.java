package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.QwenAiService;
import com.nevinsight.model.entity.core.WebSearchNews;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardInsightAgent {

    private static final int BATCH_SIZE = 6;
    private static final int CONTENT_LIMIT = 2600;
    private static final int OCR_LIMIT = 1200;

    private static final String SYSTEM_PROMPT =
            "你是猛士车型品牌的市场分析员。口径说明：猛士是车型品牌/产品品牌，M817、M917 是猛士旗下具体车型；"
            + "你的目标不是复述新闻，而是先判断这条竞品信息是否值得猛士市场团队关注，"
            + "再提炼可用于市场投入、产品传播、终端话术和竞争策略的内容。\n"
            + "只能基于输入内容，不得编造。先判断价值，再生成内容。\n"
            + "输出必须是 JSON 数组，每个对象字段：id, is_valuable, value_score, value_tags, points, detail_highlight, confidence, hide_reason。\n"
            + "is_valuable 为布尔值；value_score 为 0-10；value_tags 是字符串数组，如价格锚点、终端转化、技术对比、传播打法、订单声量、舆情风险。\n"
            + "信息对猛士没有明确参考价值时，必须返回 is_valuable=false，value_score<6，points=[]，detail_highlight=\"\"，并写 hide_reason。\n"
            + "\n"
            + "价值判断标准：\n"
            + "只有当输入事实对猛士有明确参考价值时才展示，包括：高端新能源SUV/硬派越野竞争、价格权益变化、终端转化压力、"
            + "技术/配置对比、传播打法参考、订单/销量/声量变化、政策变化、舆情风险。\n"
            + "普通上市、普通露出、口号互动、倒计时、低价值海报，即使有车型名，也返回空。\n"
            + "非直接竞品只有代表价格带变化、技术下放、爆款订单、负面舆情、政策变化、传播破圈时才保留。\n"
            + "\n"
            + "禁止：复述标题；编造；硬提市场意义；输出模板话术；复制 OCR 原文；展示平台账号、口号、免责声明、纯倒计时、纯互动文案。\n"
            + "禁止模板化表达：提升品牌影响力、增强市场竞争力、形成传播触点、多维度升级、持续传播、值得关注。\n"
            + "\n"
            + "points：字符串数组，最多 3 条，优先 1-2 条。每条必须包含具体事实 + 对猛士的判断。不能只有价格、时间、参数或发布动作。\n"
            + "detail_highlight：字符串，从原文正文/OCR 中提取可直接展示的事实短语，只写事实明细，不写判断；用“ | ”连接，最多 8 项；无高价值事实则为空。\n"
            + "detail_highlight 的每一项必须是读者脱离原文也能理解的完整事实短语，优先提取价格、权益、配置、技术、续航、动力、电池、底盘、智驾、座舱、订单/销量/交付等具体信息。\n"
            + "禁止输出需要读者回看原文才能理解的碎片：纯数字，例如“52”“50”“630”“100”“900”；孤立型号，例如“M9”“M817”“Ultra”；孤立日期，例如“5月29日”“6月”；无单位金额，例如“64.98”；无字段名参数，例如“630”“100kWh”如果无法确认是续航还是电池容量，不要输出。\n"
            + "如果原文/OCR 中已有完整短语，直接提取，例如：“64.98万元起”“至高5.2万元权益”“CLTC 630km”“电池容量100kWh”“涉水深度900mm”“巨鲸75电池包”“三电机四驱+2.0T增程器”。\n"
            + "如果原文/OCR 只有裸数字，无法确认字段名、单位或业务含义，直接删除该项，不要补单位、不要猜含义、不要改写成看似完整的参数。\n"
            + "在返回 JSON 前，逐项检查 detail_highlight；不合格项直接删除。\n"
            + "detail_highlight 必须是纯文本，不要使用 Markdown，不要输出 **、列表符号、括号解释或换行；卡片渲染会自动高亮重要词。\n"
            + "视觉高亮规则：只允许在 points 中，用 Markdown **加粗** 1-3 个真正重要的事实锚点；detail_highlight 禁止加粗。\n"
            + "只允许加粗：关键技术/配置/平台名、具体参数、权益金额/金融周期、订单/销量/交付数字、赛事成绩/活动时间地点、舆情事件。\n"
            + "禁止加粗抽象判断词：市场份额、竞争压力、市场需求、品牌宣传、产品优势、值得关注、对猛士有参考等。\n"
            + "如果没有值得加粗的具体技术或数字，就不要强行加粗。\n"
            + "\n"
            + "分板块 detail_highlight 要求：\n"
            + "launch 产品动态：配置、参数、技术、座舱、智驾、底盘、续航、通过性。\n"
            + "price_finance 价格金融：权益金额、置换补贴、免息/低息、订金膨胀、选配金、礼包、服务、截止时间。\n"
            + "campaign 营销传播：活动名称、时间地点、参赛车型/成绩、合作对象、用户参与机制。\n"
            + "strategic_action 战略动作：只提炼品牌级/公司级/产业链级长期动作，如技术路线、产品矩阵/路线、平台架构、供应链/合作伙伴、产能/工厂、出海、组织/资本动作。"
            + "普通车型上市、预售、亮相、配置升级、价格权益不属于战略动作，应返回空。\n"
            + "market_hot 市场热点：订单量、销量/交付、技术点、政策点、舆情点；没有则为空。\n"
            + "\n"
            + "好例子：\n"
            + "- point: 问界M9首搭**六激光雷达矩阵**和**巨鲸电池平台3.0**，强化高端家庭SUV的智驾与能量平台心智，猛士需要在智能越野和能量平台话术上形成差异。\n"
            + "- detail_highlight: 六激光雷达矩阵 | 巨鲸电池平台3.0 | 华为乾崑智驾ADS 5 | 全主动悬架\n"
            + "- point: **至高5.2万元购车权益**叠加老车主增换购减免，会强化用户对高端SUV价格锚点的敏感度，猛士终端需要准备权益对比话术。\n"
            + "- detail_highlight: 至高5.2万元购车权益 | 2万元选配金 | 2万元智驾高阶包补贴 | 老车主至高5万元尾款减免";

    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    public Map<Long, CardInsight> generate(List<CardInsightInput> inputs) {
        if (inputs == null || inputs.isEmpty()) return Collections.emptyMap();
        LinkedHashMap<Long, CardInsight> out = new LinkedHashMap<>();
        int failed = 0;
        for (int i = 0; i < inputs.size(); i += BATCH_SIZE) {
            List<CardInsightInput> batch = inputs.subList(i, Math.min(i + BATCH_SIZE, inputs.size()));
            Map<Long, CardInsight> batchResult = generateBatch(batch);
            if (batchResult == null) {
                for (CardInsightInput input : batch) {
                    Map<Long, CardInsight> single = generateBatch(Collections.singletonList(input));
                    if (single == null) {
                        failed++;
                    } else {
                        out.putAll(single);
                    }
                }
            } else {
                out.putAll(batchResult);
                List<CardInsightInput> missing = batch.stream()
                        .filter(input -> input.getNews() != null
                                && input.getNews().getId() != null
                                && !batchResult.containsKey(input.getNews().getId()))
                        .collect(Collectors.toList());
                for (CardInsightInput input : missing) {
                    Map<Long, CardInsight> single = generateBatch(Collections.singletonList(input));
                    if (single == null) {
                        failed++;
                    } else {
                        out.putAll(single);
                    }
                }
            }
        }
        long success = out.values().stream()
                .filter(x -> (x.getPoints() != null && !x.getPoints().isEmpty())
                        || (x.getDetailHighlight() != null && !x.getDetailHighlight().isEmpty()))
                .count();
        long empty = out.values().stream()
                .filter(x -> x.getPoints() == null || x.getPoints().isEmpty())
                .count();
        log.info("[CardInsightAgent] insight_inputs={} llm_success={} llm_empty={} llm_failed={}",
                inputs.size(), success, empty, failed);
        return out;
    }

    private Map<Long, CardInsight> generateBatch(List<CardInsightInput> batch) {
        String prompt = buildPrompt(batch);
        String response;
        try {
            response = qwenAiService.chatWithReportModelHttp(SYSTEM_PROMPT, prompt);
        } catch (Exception e) {
            log.warn("[CardInsightAgent] LLM failed batch size={}: {}", batch.size(), e.getMessage());
            return null;
        }
        return parseResponse(response, batch);
    }

    private String buildPrompt(List<CardInsightInput> batch) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < batch.size(); i++) {
            CardInsightInput input = batch.get(i);
            WebSearchNews n = input.getNews();
            sb.append("{")
              .append("\"id\":").append(n.getId()).append(',')
              .append("\"section\":").append(jsonStr(input.getSection())).append(',')
              .append("\"event_type\":").append(jsonStr(n.getEventType())).append(',')
              .append("\"source_tool\":").append(jsonStr(n.getSourceTool())).append(',')
              .append("\"brand\":").append(jsonStr(n.getBrandName())).append(',')
              .append("\"model\":").append(jsonStr(n.getModelMentioned())).append(',')
              .append("\"title\":").append(jsonStr(n.getTitle())).append(',')
              .append("\"event_summary\":").append(jsonStr(n.getEventSummary())).append(',')
              .append("\"content\":").append(jsonStr(limit(n.getContent(), CONTENT_LIMIT))).append(',')
              .append("\"image_ocr_text\":").append(jsonStr(limit(n.getImageOcrText(), OCR_LIMIT)))
              .append("}");
            if (i < batch.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("]\n\n");
        sb.append("请返回纯 JSON 数组，与输入 id 对齐。");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, CardInsight> parseResponse(String response, List<CardInsightInput> inputs) {
        if (response == null || response.trim().isEmpty()) return null;
        Map<Long, CardInsightInput> inputById = new LinkedHashMap<>();
        if (inputs != null) {
            for (CardInsightInput input : inputs) {
                if (input != null && input.getNews() != null && input.getNews().getId() != null) {
                    inputById.put(input.getNews().getId(), input);
                }
            }
        }
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
            LinkedHashMap<Long, CardInsight> out = new LinkedHashMap<>();
            for (Map<String, Object> item : arr) {
                Long id = toLong(item.get("id"));
                if (id == null) continue;
                CardInsight insight = new CardInsight();
                insight.setId(id);
                CardInsightInput input = inputById.get(id);
                insight.setValuable(toBoolean(item.get("is_valuable")));
                insight.setValueScore(toDouble(item.get("value_score"), 0.0d));
                insight.setValueTags(toStringListLoose(item.get("value_tags")));
                boolean valuable = Boolean.TRUE.equals(insight.getValuable())
                        && insight.getValueScore() != null
                        && insight.getValueScore() >= 6.0d;
                insight.setPoints(valuable ? toStringList(item.get("points"), input) : Collections.emptyList());
                insight.setDetailHighlight(valuable ? cleanDetailHighlight(toStr(item.get("detail_highlight")), input) : "");
                insight.setConfidence(toDouble(item.get("confidence"), 0.0d));
                insight.setHideReason(toStr(item.get("hide_reason")));
                out.put(id, insight);
            }
            return out;
        } catch (Exception e) {
            log.warn("[CardInsightAgent] parse failed: {}", e.getMessage());
            return null;
        }
    }

    private static List<String> toStringList(Object value, CardInsightInput input) {
        if (!(value instanceof List)) return Collections.emptyList();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            String v = toStr(item).replaceAll("\\s+", " ").trim();
            if (isLowQualityPoint(v, input)) continue;
            if (!v.isEmpty()) out.add(v);
            if (out.size() >= 3) break;
        }
        return new ArrayList<>(out);
    }

    private static List<String> toStringListLoose(Object value) {
        if (!(value instanceof List)) return Collections.emptyList();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object item : (List<?>) value) {
            String v = toStr(item).replaceAll("\\s+", " ").trim();
            if (!v.isEmpty()) out.add(v);
            if (out.size() >= 6) break;
        }
        return new ArrayList<>(out);
    }

    private static String cleanDetailHighlight(String text, CardInsightInput input) {
        if (text == null) return "";
        String v = text.replaceAll("\\s+", " ").trim();
        v = v.replaceAll("^(图含|摘要|要点|明细)[:：]\\s*", "").trim();
        if (v.isEmpty()) return "";
        String compact = normalize(v);
        if (compact.length() < 4) return "";
        if (compact.matches(".*(官方账号|直播频道|免责声明|扫码|小程序|平台|用户协议|隐私政策).*")) return "";
        if (compact.matches(".*(提升品牌影响力|增强市场竞争力|形成传播触点|多维度升级|持续传播|值得关注).*")) return "";
        if (compact.matches(".*(^|\\D)0{2,}元.*") || compact.matches(".*限时优惠价\\d{1,2}$")) return "";
        if (compact.matches(".*(车手|领航员|DONGFENGMOTOR|GWMCCTV).*")
                && !compact.matches(".*(冠军|亚军|季军|T2E|环塔|参赛).*")) return "";

        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String raw : v.split("[|｜；;，,、\\n]")) {
            String p = raw == null ? "" : raw.trim();
            if (p.length() < 2) continue;
            String pc = normalize(p);
            if (pc.matches(".*(官方账号|直播频道|免责声明|扫码|小程序|平台|口号).*")) continue;
            if (pc.matches(".*(^|\\D)0{2,}元.*") || pc.matches("^\\d{1,2}$")) continue;
            if (pc.matches(".*(提升品牌影响力|增强市场竞争力|形成传播触点|多维度升级|持续传播).*")) continue;
            parts.add(p.length() > 28 ? p.substring(0, 28) : p);
            if (parts.size() >= 8) break;
        }
        return parts.isEmpty() ? "" : String.join(" | ", parts);
    }

    private static boolean isLowQualityPoint(String point, CardInsightInput input) {
        if (point == null) return true;
        String v = point.replaceAll("\\s+", "").trim();
        if (v.length() < 10) return true;
        if (input != null && input.getNews() != null) {
            String title = normalize(input.getNews().getTitle());
            String summary = normalize(input.getNews().getEventSummary());
            String comparable = normalize(point);
            boolean hasValueBeyondTitle = v.matches(".*(说明|意味着|形成|带来|有助于|强化|提升|压力|参考|观察|猛士|用户|市场|终端|转化|声量|心智|线索|策略|价格带|配置普及|圈层|流量|分流|锚点).*")
                    || v.matches(".*(权益|优惠|补贴|置换|0息|免息|金融|保险|订单|大定|销量|交付|冠军|亚军|环塔|车展|试驾|代言|共创|智驾|激光雷达|电池|续航|底盘|四驱|座舱|空间).*");
            if (!title.isEmpty() && title.contains(comparable)) return true;
            if (!title.isEmpty() && comparable.contains(title) && !hasValueBeyondTitle) return true;
            if (!summary.isEmpty() && summary.equals(comparable) && !hasValueBeyondTitle) return true;
        }
        if (v.matches(".*(正式发布|正式亮相|召开发布会|即将举行|公布结果|开启预售|动力方面|内饰方面|配置方面|细节方面|设计方面).*")
                && !v.matches(".*(说明|意味着|形成|带来|有助于|强化|提升|压力|参考|观察|猛士|用户|市场|终端|转化|声量|心智|线索).*")) {
            return true;
        }
        if (v.matches(".*(多维度升级|配置升级|产品信息|持续传播|市场竞争力)$")) return true;
        boolean hasJudgement = v.matches(".*(说明|意味着|形成|带来|有助于|强化|提升|压力|参考|观察|猛士|用户|市场|终端|转化|声量|心智|线索|策略|价格带|配置普及|圈层|流量).*");
        String section = input == null ? "" : input.getSection();
        if ("price_finance".equals(section)
                && !v.matches(".*(权益|优惠|补贴|置换|增换购|首付|0息|零息|免息|低息|金融|贷款|保险|订金|定金|膨胀|抵扣|减免|购车礼|保养|质保|价格锚点|终端).*")) {
            return true;
        }
        if ("market_hot".equals(section) && !hasJudgement) {
            return true;
        }
        boolean purePriceOrTime = v.matches(".*(售价|预售价|指导价|起售价|万元|元|\\d{1,2}月\\d{1,2}日|\\d{1,2}[:：]\\d{2}).*")
                && !v.matches(".*(权益|优惠|补贴|置换|订单|大定|销量|交付|价格战|官降|用户|市场|压力|终端|猛士|观察|参考).*");
        if (purePriceOrTime) return true;
        boolean pureSpecs = v.matches(".*(可选|搭载|采用|配备|提供|续航|悬架|电机|功率|扭矩|座椅|屏幕|轮辋|车身).*")
                && !hasJudgement;
        return pureSpecs;
    }

    private static String normalize(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\s，,。；;：:！!？?、|\\-—–_《》<>【】\\[\\]（）()]+", "")
                .trim();
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

    private static Long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong(((String) o).trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static Double toDouble(Object o, Double fallback) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble(((String) o).trim()); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private static Boolean toBoolean(Object o) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return Boolean.parseBoolean(((String) o).trim());
        return false;
    }

    private static String toStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @Data
    public static class CardInsightInput {
        private final WebSearchNews news;
        private final String section;
    }

    @Data
    public static class CardInsight {
        private Long id;
        private Boolean valuable;
        private Double valueScore;
        private List<String> valueTags = Collections.emptyList();
        private List<String> points = Collections.emptyList();
        private String detailHighlight;
        private Double confidence;
        private String hideReason;
    }
}
