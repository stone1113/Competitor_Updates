package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.QwenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 新闻事件分类 Agent — 把单条新闻打 4 主类标签 + importance + 一句话摘要 + 车型抽取。
 *
 * 走 qwen-turbo（chatWithScreeningModelHttp），批量 5-10 条/请求节省 token + 时延。
 *
 * 4 主类（用户确认）：
 *  - launch          🚀 新车发布会/上市/改款/年款/预售
 *  - price_finance   💰 调价、0息、首付优惠、置换补贴、保险/租赁
 *  - campaign        📣 营销传播：代言人、KOL、赛事赞助、跨界联名、试驾邀约
 *  - sales_milestone 📈 月度/累计交付、订单破万、市场份额
 *  - other           兜底（OTA/召回/政策/财报...）
 *  - spam            目录页/4S 店促销/与汽车无关
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventExtractionAgent {

    private static final int CONTENT_PROMPT_LIMIT = 1200;

    public static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            "launch", "price_finance", "campaign", "sales_milestone", "other", "spam");

    private static final String SYSTEM_PROMPT =
            "你是猛士车型品牌的市场分析员，不是普通摘要机器人。口径说明：猛士是车型品牌/产品品牌，M817、M917 是猛士旗下具体车型。你的目标是从互联网内容中筛选竞品关键信息，"
            + "整理成可供猛士市场投入、产品传播、终端话术和竞争策略分析使用的事件。\n" +
            "输入是 JSON 数组，每个元素含 id/title/content，可能含 source。\n" +
            "对每条返回一个 JSON 对象，字段：\n" +
            "  id            : 原样回传\n" +
            "  event_type    : launch | price_finance | campaign | sales_milestone | other | spam （必须严格小写匹配）\n" +
            "  importance    : 0-10 整数，越高越值得在日报突出\n" +
            "  event_summary : ≤80 字的一句话事件摘要（必须含品牌或车型名 + 关键数字/动作/配置/权益；必须服务市场决策）\n" +
            "  models        : 抽取出新闻中明确提及的车型名（如「问界M9」「仰望U8」），逗号分隔；无则空串\n" +
            "\n" +
            "信息价值规则：只保留会影响猛士判断的事实，包括竞品车型发布/改款、配置升级、价格权益、营销传播、销量交付。\n" +
            "忽略不可用于决策的噪声：平台账号名单、直播频道名单、纯时间段、图片免责声明、抽象口号、无车型的泛泛品牌露出。\n" +
            "如果正文只有噪声或无法判断竞品动作，event_type 标 other 或 spam，importance ≤ 2。\n" +
            "\n" +
            "event_type 判定规则（必须 title/content 正文有对应动作动词）：\n" +
            "  - launch          正文出现「上市」「发布」「正式发布」「新款」「改款」「年款」「预售」+ 具体车型且语境是产品发布\n" +
            "  - price_finance   正文出现「降价」「涨价」「调价」「0息」「免息」「首付」「置换」「补贴」「保险」「租赁」「金融」具体动作\n" +
            "  - campaign        正文出现「代言」「邀请」「KOL」「赛事」「赞助」「试驾」「发布会」「巡展」「跨界」「节日营销」「520」「情感营销」「品牌活动」\n" +
            "  - sales_milestone 必须同时含 [具体数字+单位（辆/台/万台/万辆）] + [具体品牌或车型名]" +
                                            " + [明确动词：交付/销量/订单/累计/破万/破十万]\n" +
            "      ✅ 正例：「问界M7 累计交付 45 万辆」「理想 4 月销量 3.6 万台」「乘联会 4 月新能源乘用车销量 91 万辆」\n" +
            "      ❌ 反例（标 other 不标 sales_milestone）：\n" +
            "         - 「新能源汽车销量占新车总销量 53.2%」（行业宏观比例，无绝对数）\n" +
            "         - 「新能源汽车出口同比增 1.1 倍」（同比百分比，无绝对数）\n" +
            "         - 「2026 年 4 月乘用车销量 172 万辆」（无品牌主体的行业总数）\n" +
            "         - 「新势力品牌销量集体回暖」（无具体数字）\n" +
            "      若 source 含 cpca/caam 或来自乘联会/中汽协等权威源，importance ≥ 8\n" +
            "  - other           OTA 升级、召回、行业政策、财报、技术解读等\n" +
            "  - spam            4S 店促销页、目录页、与汽车无关的内容、SEO 模板页\n" +
            "\n" +
            "**⚠️ Hashtag 不是动作**：微博/抖音的 #XXX# 仅是话题标签，**绝不能**当作事件发生。\n" +
            "  例：'520，和 #猛士汽车# 驶入山川湖海 #猛士M817# #猛士917#' → 这是 520 情感营销 (campaign)，\n" +
            "  **绝对不能**总结成「猛士发布 M817 和 M917 车型」。event_summary 必须基于正文真实动词，禁止凭 hashtag 编造。\n" +
            "  没有真实「上市/发布」动作就**不能**标 launch。\n" +
            "\n" +
            "**官方账号一手数据加权**：当 source 以 `_official` 结尾（如 weibo_official、douyin_official），\n" +
            "  说明这是品牌官方发布、零失真。如果内容真实非软文/转发，importance 至少 ≥ 6。\n" +
            "\n" +
            "**品牌错位检测**（关键！）：输入数据可能含 brand 字段（采集时的关键词所属品牌）。\n" +
            "  如果 title/content 主体在讲**完全不同的品牌**（如 brand=猛士 但 title 在讲宝马X1 优惠、\n" +
            "  brand=坦克 但 title 在讲奔驰GLS 优惠、brand=阿维塔 但 title 在讲法拉利），**event_type 必须标 spam**，\n" +
            "  importance=0。\n" +
            "\n" +
            "**输出必须是 JSON 数组**，与输入数组对齐，无 markdown，无说明文字。";

    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    /** 输入：一批新闻；输出：每条对应一个 EventResult。 */
    public List<EventResult> classifyBatch(List<NewsLite> batch) {
        if (batch == null || batch.isEmpty()) return Collections.emptyList();

        // 构造 user prompt
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < batch.size(); i++) {
            NewsLite n = batch.get(i);
            String content = n.content == null ? "" : n.content;
            if (content.length() > CONTENT_PROMPT_LIMIT) {
                content = content.substring(0, CONTENT_PROMPT_LIMIT);
            }
            sb.append("{\"id\":").append(n.id).append(",")
              .append("\"title\":").append(jsonStr(n.title)).append(",")
              .append("\"content\":").append(jsonStr(content));
            if (n.source != null && !n.source.isEmpty()) {
                sb.append(",\"source\":").append(jsonStr(n.source));
            }
            if (n.brand != null && !n.brand.isEmpty()) {
                sb.append(",\"brand\":").append(jsonStr(n.brand));
            }
            sb.append("}");
            if (i < batch.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");

        String response;
        try {
            response = qwenAiService.chatWithScreeningModelHttp(SYSTEM_PROMPT, sb.toString());
        } catch (Exception e) {
            log.warn("[EventExtractionAgent] LLM 调用失败 batch size={}: {}", batch.size(), e.getMessage());
            return Collections.emptyList();
        }

        return parseResponse(response, batch);
    }

    private List<EventResult> parseResponse(String response, List<NewsLite> batch) {
        if (response == null || response.isEmpty()) return Collections.emptyList();
        String cleaned = response.replaceAll("```json\\s*", "")
                                  .replaceAll("```\\s*", "")
                                  .trim();
        // 兼容 LLM 包了一层 {"results": [...]} 的情况
        try {
            // 先试着按数组解
            int firstBracket = cleaned.indexOf('[');
            int lastBracket = cleaned.lastIndexOf(']');
            if (firstBracket >= 0 && lastBracket > firstBracket) {
                cleaned = cleaned.substring(firstBracket, lastBracket + 1);
            }
            List<Map<String, Object>> arr = objectMapper.readValue(cleaned, List.class);
            Map<Long, NewsLite> idToInput = new HashMap<>();
            for (NewsLite n : batch) idToInput.put(n.id, n);

            List<EventResult> out = new ArrayList<>();
            for (Map<String, Object> m : arr) {
                Long id = toLong(m.get("id"));
                String type = toStr(m.get("event_type"));
                if (!ALLOWED_EVENT_TYPES.contains(type)) type = "other";
                Integer importance = toInt(m.get("importance"));
                if (importance == null) importance = 5;
                importance = Math.max(0, Math.min(10, importance));
                String summary = toStr(m.get("event_summary"));
                String models = toStr(m.get("models"));
                if (id == null) continue;
                out.add(new EventResult(id, type, importance, summary, models));
            }
            return out;
        } catch (Exception e) {
            log.error("[EventExtractionAgent] 解析响应失败 ({}): {}",
                    e.getMessage(),
                    cleaned.length() > 200 ? cleaned.substring(0, 200) + "…" : cleaned);
            return Collections.emptyList();
        }
    }

    private static String jsonStr(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", " ").replace("\r", "").trim() + "\"";
    }

    private static Long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong(((String) o).trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt(((String) o).trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static String toStr(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /** 单条新闻输入。 */
    public static class NewsLite {
        public final Long id;
        public final String title;
        public final String content;
        /** source_tool 名，让 LLM 判断官方账号加权（如 weibo_official） */
        public final String source;
        /** 采集关键词所属 brand_name，让 LLM 判断 brand 错位 → spam */
        public final String brand;
        public NewsLite(Long id, String title, String content) {
            this(id, title, content, null, null);
        }
        public NewsLite(Long id, String title, String content, String source) {
            this(id, title, content, source, null);
        }
        public NewsLite(Long id, String title, String content, String source, String brand) {
            this.id = id; this.title = title; this.content = content;
            this.source = source; this.brand = brand;
        }
    }

    /** 一条分类结果。 */
    public static class EventResult {
        public final Long id;
        public final String eventType;
        public final Integer importance;
        public final String eventSummary;
        public final String models;
        public EventResult(Long id, String eventType, Integer importance, String eventSummary, String models) {
            this.id = id; this.eventType = eventType; this.importance = importance;
            this.eventSummary = eventSummary; this.models = models;
        }
    }
}
