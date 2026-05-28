package com.nevinsight.report.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.agent.CardGroupSummaryAgent.CardGroupSummary;
import com.nevinsight.intelligence.agent.CardGroupSummaryAgent.GroupSummaryInput;
import com.nevinsight.intelligence.agent.CardGroupSummaryAgent.GroupSummaryItem;
import com.nevinsight.intelligence.agent.CardInsightAgent.CardInsight;
import com.nevinsight.model.dto.response.CardTextSections;
import com.nevinsight.model.entity.core.GasgooSalesRecord;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.report.pipeline.CompetitorReportPipeline.BenchmarkMatrix;
import com.nevinsight.report.pipeline.CompetitorReportPipeline.ParamRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.nevinsight.report.feishu.FeishuCardUtils.*;

/**
 * 竞品分析日报飞书卡片 v3（事件驱动）。
 *
 * 板块：
 *  📋 今日速览       — LLM 汇总全部竞品动态 + 风险提示 + 应对策略
 *  🚀 产品动态      — 微博官号内容按车型聚合总结 + 链接
 *  💰 价格 & 金融   — 同上
 *  📣 营销传播      — 同上
 *  📈 销量 & 交付   — 同上
 *  ⚔️ 技术对标矩阵  — SQL，不变
 *  💬 销售应对话术  — 按 persona 折叠（v2 保留）
 *  🔍 深度对标按钮
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorCardBuilder {

    private final ObjectMapper objectMapper;

    /** 单板块 byte 上限（飞书 section 硬限 2500，超出截断） */
    private static final int SECTION_BYTE_LIMIT = 2400;
    /** 每个品牌/车型组最多展示的动态明细数，仍受 section byte 上限保护。 */
    private static final int MAX_GROUP_DISPLAY_ITEMS = 8;

    /** event_type → 中文标题 */
    private static final Map<String, String> EVENT_TYPE_LABEL = Map.of(
            "launch",          "🚀 产品动态",
            "price_finance",   "💰 价格金融",
            "campaign",        "📣 营销传播",
            "strategic_action", "🧭 战略动作",
            "sales_milestone", "📈 销量 & 交付"
    );
    private static final String[] EVENT_ORDER = {"launch", "price_finance", "campaign", "sales_milestone"};

    /** source_tool → 中文显示名 */
    private static final Map<String, String> SOURCE_TOOL_LABEL = Map.of(
            "weibo_official",   "微博官号",
            "douyin_official",  "抖音官号",
            "xhs_official",     "小红书官号",
            "bocha",            "Bocha 新闻"
    );

    private static final DateTimeFormatter PUBLISH_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_DATE_FMT =
            DateTimeFormatter.ofPattern("M月d日");
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    /** 品牌色 oxblood — 本品 + 警示重点 */
    private static final String COLOR_BRAND = "red";
    /** 竞品销量领先用 — 警示 */
    private static final String COLOR_THREAT = "green";

    public String build(String brandName, LocalDate date,
                        String briefing,
                        Map<String, List<WebSearchNews>> byEvent,
                        List<BenchmarkMatrix> matrices,
                        List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                        String deepAnalysisUrl) {
        return build(brandName, date, briefing, byEvent, matrices, talkingPoints,
                deepAnalysisUrl, null, new LinkedHashMap<>());
    }

    public String build(String brandName, LocalDate date,
                        String briefing,
                        Map<String, List<WebSearchNews>> byEvent,
                        List<BenchmarkMatrix> matrices,
                        List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                        String deepAnalysisUrl,
                        String frontendBaseUrl) {
        return build(brandName, date, briefing, byEvent, matrices, talkingPoints,
                deepAnalysisUrl, frontendBaseUrl, new LinkedHashMap<>());
    }

    /** v8: 接受盖世销量按 vs_self_model 分组（每对标关系一张表）。 */
    public String build(String brandName, LocalDate date,
                        String briefing,
                        Map<String, List<WebSearchNews>> byEvent,
                        List<BenchmarkMatrix> matrices,
                        List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                        String deepAnalysisUrl,
                        String frontendBaseUrl,
                        Map<String, List<GasgooSalesRecord>> gasgooSalesByVs) {
        return build(brandName, date, briefing, byEvent, matrices, talkingPoints,
                deepAnalysisUrl, frontendBaseUrl, gasgooSalesByVs,
                new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    /** v8+: 加按对标车型最新金融政策板块。 */
    public String build(String brandName, LocalDate date,
                        String briefing,
                        Map<String, List<WebSearchNews>> byEvent,
                        List<BenchmarkMatrix> matrices,
                        List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                        String deepAnalysisUrl,
                        String frontendBaseUrl,
                        Map<String, List<GasgooSalesRecord>> gasgooSalesByVs,
                        Map<String, List<WebSearchNews>> financeByVs,
                        Map<String, String> modelToVsSelf) {
        return build(brandName, date, briefing, byEvent, matrices, talkingPoints,
                deepAnalysisUrl, frontendBaseUrl, gasgooSalesByVs,
                financeByVs, modelToVsSelf, Collections.emptyList());
    }

    /** v9: 加市场热度板块。 */
    public String build(String brandName, LocalDate date,
                        String briefing,
                        Map<String, List<WebSearchNews>> byEvent,
                        List<BenchmarkMatrix> matrices,
                        List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                        String deepAnalysisUrl,
                        String frontendBaseUrl,
                        Map<String, List<GasgooSalesRecord>> gasgooSalesByVs,
                        Map<String, List<WebSearchNews>> financeByVs,
                        Map<String, String> modelToVsSelf,
                        List<WebSearchNews> marketHotEvents) {
        return build(brandName, date, briefing, byEvent, matrices, talkingPoints,
                deepAnalysisUrl, frontendBaseUrl, gasgooSalesByVs,
                financeByVs, modelToVsSelf, marketHotEvents, Collections.emptyMap());
    }

    /** v10: 卡片要点优先使用大模型提炼结果；无结果时降级到本地规则。 */
    public String build(String brandName, LocalDate date,
                        String briefing,
                        Map<String, List<WebSearchNews>> byEvent,
                        List<BenchmarkMatrix> matrices,
                        List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                        String deepAnalysisUrl,
                        String frontendBaseUrl,
                        Map<String, List<GasgooSalesRecord>> gasgooSalesByVs,
                        Map<String, List<WebSearchNews>> financeByVs,
                        Map<String, String> modelToVsSelf,
                        List<WebSearchNews> marketHotEvents,
                        Map<Long, CardInsight> cardInsights) {
        return build(brandName, date, briefing, byEvent, matrices, talkingPoints,
                deepAnalysisUrl, frontendBaseUrl, gasgooSalesByVs, financeByVs,
                modelToVsSelf, marketHotEvents, cardInsights, Collections.emptyMap());
    }

    /** v11: 分组总结使用基于最终展示明细的 LLM 结果；无结果则不展示总结行。 */
    public String build(String brandName, LocalDate date,
                        String briefing,
                        Map<String, List<WebSearchNews>> byEvent,
                        List<BenchmarkMatrix> matrices,
                        List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                        String deepAnalysisUrl,
                        String frontendBaseUrl,
                        Map<String, List<GasgooSalesRecord>> gasgooSalesByVs,
                        Map<String, List<WebSearchNews>> financeByVs,
                        Map<String, String> modelToVsSelf,
                        List<WebSearchNews> marketHotEvents,
                        Map<Long, CardInsight> cardInsights,
                        Map<String, CardGroupSummary> groupSummaries) {
        try {
            Map<Long, CardInsight> insights = cardInsights == null ? Collections.emptyMap() : cardInsights;
            Map<String, CardGroupSummary> summaries = groupSummaries == null ? Collections.emptyMap() : groupSummaries;
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("msg_type", "interactive");

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("schema", "2.0");
            card.put("config", Collections.singletonMap("width_mode", "fill"));

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("template", "blue");
            header.put("title", textNode("plain_text",
                    String.format("%s · %s 竞品动态", brandName, date)));
            card.put("header", header);

            List<Object> elements = new ArrayList<>();

            // ===== v8+ 顶部总结：briefing 移到 header 之后第一位 =====
            if (briefing != null && !briefing.trim().isEmpty()) {
                elements.add(divNode("lark_md",
                        "**📊 今日速览**\n" + renderStructuredBriefing(briefing)));
            }

            // ===== v8+ KPI 横排（column_set 4 列）=====
            int launchCnt = byEvent == null ? 0
                    : byEvent.getOrDefault("launch", Collections.emptyList()).size();
            int priceCnt = byEvent == null ? 0
                    : byEvent.getOrDefault("price_finance", Collections.emptyList()).size();
            int campaignCnt = byEvent == null ? 0
                    : byEvent.getOrDefault("campaign", Collections.emptyList()).size();
            int salesCnt = gasgooSalesByVs == null ? 0
                    : gasgooSalesByVs.values().stream().mapToInt(List::size).sum();
            elements.add(hr());
            elements.add(kpiRow(launchCnt, priceCnt, campaignCnt, salesCnt));

            // ===== 4 类事件板块（v8+: campaign 折叠，sales 走结构化）=====
            int totalEvents = 0;
            if (byEvent != null) {
                for (String et : EVENT_ORDER) {
                    List<WebSearchNews> rows = byEvent.getOrDefault(et, Collections.emptyList());

                    if ("sales_milestone".equals(et)) {
                        String salesSection = renderSalesSection(gasgooSalesByVs);
                        if (salesSection != null) {
                            elements.add(hr());
                            elements.add(divNode("lark_md", salesSection));
                            int total = gasgooSalesByVs == null ? 0
                                    : gasgooSalesByVs.values().stream().mapToInt(List::size).sum();
                            totalEvents += total;
                        }
                        continue;
                    }
                    if (rows.isEmpty()) continue;
                    totalEvents += rows.size();

                    String eventSection = renderEventSection(et, rows, insights, summaries);
                    if (eventSection == null || eventSection.trim().isEmpty()) continue;
                    elements.add(hr());
                    elements.add(divNode("lark_md", eventSection));
                }
            }
            if (totalEvents == 0) {
                elements.add(hr());
                elements.add(divNode("lark_md",
                        "<font color='grey'>**📭 暂无已分类的高价值事件**</font>\n"
                                + "可能原因：① Bocha 未采集新数据；② 事件分类任务未运行。"));
            }

            String marketHotSection = renderMarketHotSection(marketHotEvents, insights);
            if (marketHotSection != null && !marketHotSection.isEmpty()) {
                elements.add(hr());
                elements.add(divNode("lark_md", marketHotSection));
            }

            // ===== ⚔️ 技术对标矩阵（改为跳转按钮，详细数据看 H5 页）=====
            if (matrices != null && !matrices.isEmpty()) {
                elements.add(hr());
                StringBuilder benchHeader = new StringBuilder("**⚔️ 技术对标矩阵**");
                int totalCompetitors = matrices.stream()
                        .mapToInt(m -> Math.max(0, m.modelNames.size() - 1)).sum();
                int totalParams = matrices.stream()
                        .mapToInt(m -> m.paramRows.size()).sum();
                benchHeader.append("　<font color='grey'>")
                      .append(matrices.size()).append(" 桌 · ")
                      .append(totalCompetitors).append(" 款竞品 · ")
                      .append(totalParams).append(" 参数项</font>");
                elements.add(divNode("lark_md", benchHeader.toString()));

                // 每桌一行简介（不再为每桌出独立跳转按钮，统一走下方智能对标入口）
                for (BenchmarkMatrix matrix : matrices) {
                    String selfModel = matrix.selfModel == null ? "对标" : matrix.selfModel;
                    int competitorCount = Math.max(0, matrix.modelNames.size() - 1);
                    String summary = buildBenchmarkSummary(matrix);

                    StringBuilder line = new StringBuilder();
                    line.append("**🔸 ").append(selfModel).append("**　")
                        .append("<font color='grey'>vs ").append(competitorCount)
                        .append(" 款竞品 · ").append(matrix.paramRows.size())
                        .append(" 参数</font>");
                    if (!summary.isEmpty()) {
                        line.append("\n").append(summary);
                    }
                    elements.add(divNode("lark_md", line.toString()));
                }
            }

            // ===== 智能深度对标统一入口（技术对标矩阵下方唯一按钮）=====
            String h5Base = (frontendBaseUrl == null || frontendBaseUrl.isEmpty())
                    ? "http://localhost:3080" : frontendBaseUrl;
            String benchmarkUrl = h5Base + "/h5/benchmark";
            elements.add(actionButton("🤖 智能竞品对标", benchmarkUrl));

            elements.add(noteFooter());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("direction", "vertical");
            body.put("elements", elements);
            card.put("body", body);
            root.put("card", card);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("[CompetitorCard] 构建失败: {}", e.getMessage(), e);
            return "{\"msg_type\":\"text\",\"content\":{\"text\":\"竞品日报生成失败: " + e.getMessage() + "\"}}";
        }
    }

    /** v8+ 事件板块：标题 + body（含 N 条计数）。 */
    private String renderEventSection(String eventType, List<WebSearchNews> rows,
                                      Map<Long, CardInsight> cardInsights,
                                      Map<String, CardGroupSummary> groupSummaries) {
        String label = EVENT_TYPE_LABEL.getOrDefault(eventType, eventType);
        String body;
        if ("launch".equals(eventType)) {
            body = renderProductDynamicsBody(rows, cardInsights, groupSummaries);
        } else if ("price_finance".equals(eventType) || "campaign".equals(eventType)
                || "strategic_action".equals(eventType)) {
            body = renderGroupedEventBody(eventType, rows, cardInsights, groupSummaries);
        } else {
            body = renderEventBody(eventType, rows, cardInsights);
        }
        if (body == null || body.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(label).append("**　<font color='grey'>")
          .append(rows.size()).append(" 条</font>\n");
        sb.append(body);
        return sb.toString();
    }

    private String renderGroupedEventBody(String eventType, List<WebSearchNews> rows,
                                          Map<Long, CardInsight> cardInsights,
                                          Map<String, CardGroupSummary> groupSummaries) {
        if (rows == null || rows.isEmpty()) return "";
        LinkedHashMap<String, List<WebSearchNews>> grouped = groupProductEventsByModel(rows);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<WebSearchNews>> e : grouped.entrySet()) {
            if (sb.length() > SECTION_BYTE_LIMIT) break;
            String group = e.getKey();
            DisplayedGroup displayed = buildDisplayedGroup(eventType, group, e.getValue(), cardInsights);
            if (displayed.items.isEmpty()) continue;
            StringBuilder groupBody = new StringBuilder();
            int displayIndex = 1;
            for (DisplayedItem item : displayed.items) {
                WebSearchNews x = item.row;
                String title = item.title;
                groupBody.append("　").append(displayIndex++).append(". ");
                String url = x.getUrl();
                if (url != null && !url.isEmpty()) {
                    groupBody.append("[").append(safe(title)).append("](").append(url).append(")");
                } else {
                    groupBody.append(safe(title));
                }
                groupBody.append(renderProductSourceLine(x)).append("\n");
                if (item.ocrPart != null && !item.ocrPart.isEmpty()
                        && !isDuplicateSnippet(item.ocrPart, title, item.contentSnippet)) {
                    groupBody.append("　<font color='").append(COLOR_BRAND).append("'>🔍 ")
                      .append(highlightImportant(item.ocrPart)).append("</font>\n");
                }
            }
            if (groupBody.length() == 0) continue;
            sb.append("**").append(safe(group)).append("**　<font color='grey'>")
              .append(formatProductSourceCounts(displayed.weiboCount, displayed.webCount)).append("</font>\n");
            String summary = lookupGroupSummary(groupSummaries, displayed.groupKey);
            if (!summary.isEmpty()) {
                sb.append("总结：").append(highlightImportant(truncateAtSentenceBoundary(summary, 92))).append("\n");
            }
            sb.append(groupBody);
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String buildGroupedEventSummary(String eventType, String group, List<WebSearchNews> items,
                                                   Map<Long, CardInsight> cardInsights) {
        if (items == null || items.isEmpty()) {
            return group + ("price_finance".equals(eventType) ? "发布权益/金融政策。" : "开展营销传播。");
        }
        String highlight = buildGroupOcrHighlight(eventType, items);
        if ("price_finance".equals(eventType) && highlight != null && !highlight.isEmpty()) {
            return truncate(group + "重点释放" + highlight + "。", 86);
        }
        if ("campaign".equals(eventType) && highlight != null && !highlight.isEmpty()) {
            return truncate(group + "围绕" + highlight + "形成传播触点。", 86);
        }
        for (WebSearchNews item : items) {
            String title = buildProductItemTitle(item);
            String snippet = buildLlmInsightSnippet(cardInsights, item, title, null);
            String first = firstInsightLine(snippet);
            if (!first.isEmpty()) return truncateAtSentenceBoundary(first, 86);
        }
        String title = buildProductItemTitle(items.get(0));
        return truncate(group + "围绕" + title + "展开传播。", 86);
    }

    private static String firstInsightLine(String snippet) {
        if (snippet == null || snippet.trim().isEmpty()) return "";
        String cleaned = snippet.replaceAll("\\n　+\\d+\\.\\s*", "\n")
                .replaceAll("^\\s*\\d+\\.\\s*", "")
                .trim();
        String[] lines = cleaned.split("\\n+");
        for (String line : lines) {
            String v = cleanDigestPoint(line);
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    /** v11: 由卡片最终展示明细反推分组总结输入，保证总结和底下明细同口径。 */
    public List<GroupSummaryInput> buildGroupSummaryInputs(Map<String, List<WebSearchNews>> byEvent,
                                                           Map<Long, CardInsight> cardInsights) {
        if (byEvent == null || byEvent.isEmpty()) return Collections.emptyList();
        Map<Long, CardInsight> insights = cardInsights == null ? Collections.emptyMap() : cardInsights;
        List<GroupSummaryInput> inputs = new ArrayList<>();
        for (String eventType : Arrays.asList("launch", "price_finance", "campaign")) {
            List<WebSearchNews> rows = byEvent.get(eventType);
            if (rows == null || rows.isEmpty()) continue;
            LinkedHashMap<String, List<WebSearchNews>> grouped = groupProductEventsByModel(rows);
            for (Map.Entry<String, List<WebSearchNews>> e : grouped.entrySet()) {
                DisplayedGroup displayed = buildDisplayedGroup(eventType, e.getKey(), e.getValue(), insights);
                if (displayed.items.isEmpty()) continue;
                inputs.add(toGroupSummaryInput(eventType, e.getKey(), displayed));
            }
        }
        return inputs;
    }

    private static GroupSummaryInput toGroupSummaryInput(String eventType, String group, DisplayedGroup displayed) {
        List<GroupSummaryItem> items = new ArrayList<>();
        for (int i = 0; i < displayed.items.size(); i++) {
            DisplayedItem item = displayed.items.get(i);
            String detail = item.ocrPart;
            items.add(new GroupSummaryItem(
                    item.row == null ? null : item.row.getId(),
                    item.title,
                    sourceLabel(item.row == null ? null : item.row.getSourceTool()),
                    splitInsightLines(item.contentSnippet),
                    detail));
        }
        return new GroupSummaryInput(displayed.groupKey, eventType, group, items);
    }

    private static List<String> splitInsightLines(String snippet) {
        if (snippet == null || snippet.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        String cleaned = snippet.replaceAll("\\n　+\\d+\\.\\s*", "\n")
                .replaceAll("^\\s*\\d+\\.\\s*", "")
                .trim();
        for (String line : cleaned.split("\\n+")) {
            String v = cleanDigestPoint(line);
            if (!v.isEmpty()) out.add(v);
            if (out.size() >= 3) break;
        }
        return out;
    }

    private DisplayedGroup buildDisplayedGroup(String eventType, String group, List<WebSearchNews> rawItems,
                                               Map<Long, CardInsight> cardInsights) {
        List<WebSearchNews> items = sortProductItems(dedupeProductItems(rawItems));
        List<WebSearchNews> selected = selectProductDisplayItems(items);
        List<DisplayedItem> displayedItems = new ArrayList<>();
        for (WebSearchNews x : selected) {
            String title = buildProductItemTitle(x);
            String ocrPart = buildLlmDetailHighlight(cardInsights, x, title);
            String contentSnippet = buildReadableContentSnippet(eventType, x, title, ocrPart, cardInsights);
            if (!"launch".equals(eventType) && !"campaign".equals(eventType)
                    && contentSnippet.isEmpty() && (ocrPart == null || ocrPart.isEmpty())) {
                continue;
            }
            displayedItems.add(new DisplayedItem(x, title, contentSnippet, ocrPart));
        }
        String groupHighlight = "";
        if ("launch".equals(eventType) && !displayedItems.isEmpty()) {
            groupHighlight = aggregateDisplayedDetails(displayedItems);
        }
        String groupKey = buildGroupSummaryKey(eventType, group, displayedItems);
        int weiboCount = countDisplayedSource(displayedItems, true);
        int webCount = countDisplayedSource(displayedItems, false);
        return new DisplayedGroup(groupKey, displayedItems, weiboCount, webCount, groupHighlight);
    }

    private static String buildGroupSummaryKey(String eventType, String group, List<DisplayedItem> items) {
        String ids = items == null ? "" : items.stream()
                .map(item -> item.row != null && item.row.getId() != null
                        ? String.valueOf(item.row.getId())
                        : normalizeComparable(item.title))
                .collect(Collectors.joining(","));
        return eventType + "|" + normalizeComparable(group) + "|" + ids;
    }

    private static int countDisplayedSource(List<DisplayedItem> items, boolean weibo) {
        if (items == null || items.isEmpty()) return 0;
        int count = 0;
        for (DisplayedItem item : items) {
            String source = item.row == null || item.row.getSourceTool() == null ? "" : item.row.getSourceTool();
            boolean isWeibo = source.contains("weibo");
            if (weibo == isWeibo) count++;
        }
        return count;
    }

    private static String lookupGroupSummary(Map<String, CardGroupSummary> summaries, String key) {
        if (summaries == null || summaries.isEmpty() || key == null || key.isEmpty()) return "";
        CardGroupSummary summary = summaries.get(key);
        if (summary == null || summary.getSummary() == null) return "";
        return summary.getSummary().trim();
    }

    private static String aggregateDisplayedDetails(List<DisplayedItem> items) {
        if (items == null || items.isEmpty()) return "";
        LinkedHashSet<String> points = new LinkedHashSet<>();
        for (DisplayedItem item : items) {
            if (item == null || item.ocrPart == null || item.ocrPart.isEmpty()) continue;
            for (String raw : item.ocrPart.split("[|｜；;]")) {
                String v = cleanDigestPoint(raw);
                if (v.isEmpty() || isLowValueLlmDetail(v)) continue;
                points.add(v);
                if (points.size() >= 8) break;
            }
            if (points.size() >= 8) break;
        }
        return points.isEmpty() ? "" : truncate(String.join(" | ", points), 160);
    }

    private static class DisplayedGroup {
        final String groupKey;
        final List<DisplayedItem> items;
        final int weiboCount;
        final int webCount;
        final String groupHighlight;

        DisplayedGroup(String groupKey, List<DisplayedItem> items, int weiboCount, int webCount, String groupHighlight) {
            this.groupKey = groupKey;
            this.items = items == null ? Collections.emptyList() : items;
            this.weiboCount = weiboCount;
            this.webCount = webCount;
            this.groupHighlight = groupHighlight == null ? "" : groupHighlight;
        }
    }

    private static class DisplayedItem {
        final WebSearchNews row;
        final String title;
        final String contentSnippet;
        final String ocrPart;

        DisplayedItem(WebSearchNews row, String title, String contentSnippet, String ocrPart) {
            this.row = row;
            this.title = title == null ? "" : title;
            this.contentSnippet = contentSnippet == null ? "" : contentSnippet;
            this.ocrPart = ocrPart == null ? "" : ocrPart;
        }
    }

    /**
     * 产品动态：按品牌/车系品牌聚合，一组输出一段总结和最多 2 条官号 + 1 条网页。
     * 同一条联合新闻只归入一个品牌组，避免 U7/U8/U9 这类多车型信息重复展开。
     */
    private String renderProductDynamicsBody(List<WebSearchNews> rows, Map<Long, CardInsight> cardInsights,
                                             Map<String, CardGroupSummary> groupSummaries) {
        if (rows == null || rows.isEmpty()) return "";
        LinkedHashMap<String, List<WebSearchNews>> grouped = groupProductEventsByModel(rows);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<WebSearchNews>> e : grouped.entrySet()) {
            if (sb.length() > SECTION_BYTE_LIMIT) break;
            String group = e.getKey();
            DisplayedGroup displayed = buildDisplayedGroup("launch", group, e.getValue(), cardInsights);
            if (displayed.items.isEmpty()) continue;
            sb.append("**").append(safe(group)).append("**　<font color='grey'>")
              .append(formatProductSourceCounts(displayed.weiboCount, displayed.webCount)).append("</font>\n");
            String summary = lookupGroupSummary(groupSummaries, displayed.groupKey);
            if (!summary.isEmpty()) {
                sb.append("总结：").append(highlightImportant(truncateAtSentenceBoundary(summary, 92))).append("\n");
            }

            for (int i = 0; i < displayed.items.size(); i++) {
                DisplayedItem item = displayed.items.get(i);
                WebSearchNews x = item.row;
                String title = item.title;
                sb.append("　").append(i + 1).append(". ");
                String url = x.getUrl();
                if (url != null && !url.isEmpty()) {
                    sb.append("[").append(safe(title)).append("](").append(url).append(")");
                } else {
                    sb.append(safe(title));
                }
                sb.append(renderProductSourceLine(x)).append("\n");
            }

            String specHighlight = displayed.groupHighlight;
            if (specHighlight != null && !specHighlight.isEmpty()) {
                sb.append("　<font color='").append(COLOR_BRAND).append("'>🔍 ")
                  .append(highlightImportant(specHighlight)).append("</font>\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static List<WebSearchNews> sortProductItems(List<WebSearchNews> items) {
        List<WebSearchNews> sorted = new ArrayList<>(items == null ? Collections.emptyList() : items);
        sorted.sort((a, b) -> {
            int sourceCmp = Integer.compare(productSourceRank(a), productSourceRank(b));
            if (sourceCmp != 0) return sourceCmp;
            int impA = a == null || a.getEventImportance() == null ? 0 : a.getEventImportance();
            int impB = b == null || b.getEventImportance() == null ? 0 : b.getEventImportance();
            if (impA != impB) return Integer.compare(impB, impA);
            long tsA = a == null || a.getAddTs() == null ? 0L : a.getAddTs();
            long tsB = b == null || b.getAddTs() == null ? 0L : b.getAddTs();
            return Long.compare(tsB, tsA);
        });
        return sorted;
    }

    private static int productSourceRank(WebSearchNews row) {
        String tool = row == null ? null : row.getSourceTool();
        if ("weibo_official".equals(tool)) return 0;
        if (tool != null && tool.endsWith("_official")) return 1;
        if ("bocha".equals(tool)) return 2;
        return 3;
    }

    private static int countProductSource(List<WebSearchNews> items, boolean weibo) {
        int count = 0;
        if (items == null) return 0;
        for (WebSearchNews item : items) {
            if (isWeiboOfficial(item) == weibo) count++;
        }
        return count;
    }

    private static boolean isWeiboOfficial(WebSearchNews row) {
        return row != null && "weibo_official".equals(row.getSourceTool());
    }

    private static String formatProductSourceCounts(int weiboCount, int webCount) {
        List<String> parts = new ArrayList<>();
        if (weiboCount > 0) parts.add(weiboCount + " 条微博");
        if (webCount > 0) parts.add(webCount + " 条网页");
        if (parts.isEmpty()) return "0 条";
        return String.join(" / ", parts);
    }

    private static List<WebSearchNews> selectProductDisplayItems(List<WebSearchNews> sorted) {
        List<WebSearchNews> out = new ArrayList<>();
        if (sorted == null) return out;
        for (WebSearchNews item : sorted) {
            out.add(item);
            if (out.size() >= MAX_GROUP_DISPLAY_ITEMS) break;
        }
        return out;
    }

    private static String renderProductSourceLine(WebSearchNews row) {
        return "　<font color='grey'>" + sourceLabel(row == null ? null : row.getSourceTool())
                + " · " + formatRelativeTime(row == null ? null : row.getAddTs()) + "</font>";
    }

    private static List<WebSearchNews> dedupeProductItems(List<WebSearchNews> items) {
        List<WebSearchNews> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (items == null) return out;
        for (WebSearchNews item : items) {
            String key = productIdentityKey(item);
            if (!seen.add(key)) continue;
            out.add(item);
        }
        return out;
    }

    private static String productIdentityKey(WebSearchNews row) {
        if (row == null) return "";
        String url = row.getUrl();
        if (url != null && !url.trim().isEmpty()) return "url:" + url.trim();
        String title = firstNonBlank(row.getTitle(), row.getEventSummary());
        return "title:" + normalizeComparable(removeOcrSuffix(title));
    }

    private static String buildProductItemTitle(WebSearchNews row) {
        if (row == null) return "";
        String title = firstNonBlank(row.getEventSummary(), row.getTitle(), row.getContent());
        title = removeOcrSuffix(title);
        title = stripHashTags(title);
        title = title.replaceAll("\\s+", " ").trim();
        if (isNoisyOcrClause(title)) {
            title = firstNonBlank(row.getTitle(), row.getContent());
        }
        return truncate(title, MAX_SUMMARY + 20);
    }

    private static String buildReadableContentSnippet(String eventType, WebSearchNews row, String title, String existingDetail) {
        return buildReadableContentSnippet(eventType, row, title, existingDetail, Collections.emptyMap());
    }

    private static String buildReadableContentSnippet(String eventType, WebSearchNews row, String title,
                                                      String existingDetail,
                                                      Map<Long, CardInsight> cardInsights) {
        if (row == null) return "";
        return buildLlmInsightSnippet(cardInsights, row, title, existingDetail);
    }

    private static String buildCampaignReadableSnippet(WebSearchNews row, String title, String existingDetail) {
        String text = combineNonBlank(row.getContent(), removeOcrSuffix(row.getEventSummary()),
                extractMarkedImageSummary(row.getImageOcrText()), row.getTitle());
        text = normalizeContentSnippetText(text);
        if (text.isEmpty()) return "";

        String base = cleanDigestPoint(removeOcrSuffix(firstNonBlank(row.getEventSummary(), row.getTitle())));
        if (isNoisyOcrClause(base)) base = "";
        List<String> points = extractCampaignValuePoints(text, row, title, existingDetail, base, 2);
        String digest = buildDigestSentence(base, points, row);
        if (!digest.isEmpty()) return digest;

        String picked = pickUsefulSnippetClause(text, snippetKeywords("campaign"), title, existingDetail);
        picked = cleanCampaignValueClause(picked);
        if (isLowValueLlmPoint(picked)) return "";
        if (picked.isEmpty() || isDuplicateSnippet(picked, title, existingDetail)) return "";
        return toCompleteDigestSentence(picked);
    }

    private static List<String> extractDigestPoints(String text, List<String> keywords,
                                                    String title, String existingDetail, String base) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return new ArrayList<>();
        for (String raw : text.split("[\\n。；;！!？?，,、]")) {
            String clause = cleanDigestPoint(raw);
            if (clause.length() < 6 || clause.length() > 46) continue;
            if (isNoisyOcrClause(clause)) continue;
            if (isDuplicateSnippet(clause, title, existingDetail) || isDuplicateSnippet(clause, base, null)) continue;
            if (isLowValuePriceOrTimeOnly(clause) || isLowValueLlmPoint(clause)) continue;
            if (!hasDigestValue(clause, keywords)) continue;
            points.add(clause);
            if (points.size() >= 3) break;
        }
        return new ArrayList<>(points);
    }

    private static String buildDigestSentence(String base, List<String> points, WebSearchNews row) {
        String cleanBase = cleanDigestPoint(base);
        if (cleanBase.endsWith("。")) cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        if (cleanBase.length() > 65) cleanBase = truncateAtSentenceBoundary(cleanBase, 65);

        List<String> extras = new ArrayList<>();
        if (points != null) {
            for (String point : points) {
                String v = cleanDigestPoint(point);
                if (v.isEmpty()) continue;
                if (!cleanBase.isEmpty() && isDuplicateSnippet(v, cleanBase, null)) continue;
                extras.add(v);
            }
        }

        return formatInsightPoints(extras, 3);
    }

    private static String formatInsightPoints(Collection<String> rawPoints, int maxPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) return "";
        LinkedHashSet<String> points = new LinkedHashSet<>();
        for (String raw : rawPoints) {
            String point = cleanDigestPoint(raw);
            if (point.isEmpty()) continue;
            point = point.replaceAll("[。！？!?…]+$", "");
            point = truncateAtSentenceBoundary(point, 82);
            boolean duplicate = false;
            for (String existing : points) {
                if (isDuplicateSnippet(point, existing, null)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            points.add(point);
            if (points.size() >= maxPoints) break;
        }
        if (points.isEmpty()) return "";
        if (points.size() == 1) return toCompleteDigestSentence(points.iterator().next());

        StringBuilder sb = new StringBuilder("\n");
        int i = 1;
        for (String point : points) {
            sb.append("　　").append(i++).append(". ")
              .append(highlightImportant(toCompleteDigestSentence(point)))
              .append("\n");
        }
        return sb.substring(0, sb.length() - 1);
    }

    private static String buildLlmInsightSnippet(Map<Long, CardInsight> cardInsights,
                                                 WebSearchNews row,
                                                 String title,
                                                 String existingDetail) {
        if (cardInsights == null || row == null || row.getId() == null) return "";
        CardInsight insight = cardInsights.get(row.getId());
        if (insight == null || insight.getPoints() == null || insight.getPoints().isEmpty()) return "";
        if (Boolean.FALSE.equals(insight.getValuable())) return "";
        if (insight.getValueScore() != null && insight.getValueScore() < 6.0d) return "";
        if (insight.getConfidence() != null && insight.getConfidence() < 0.35d) return "";

        List<String> filtered = new ArrayList<>();
        for (String raw : insight.getPoints()) {
            String point = cleanDigestPoint(raw);
            if (point.isEmpty()) continue;
            if (isDuplicateSnippet(point, title, existingDetail)
                    && !hasInsightValueBeyondTitle(point, title)) {
                continue;
            }
            if (isLowValueLlmPoint(point)) continue;
            filtered.add(point);
        }
        return formatInsightPoints(filtered, 1);
    }

    private static String buildLlmDetailHighlight(Map<Long, CardInsight> cardInsights,
                                                  WebSearchNews row,
                                                  String title) {
        if (cardInsights == null || row == null || row.getId() == null) return "";
        CardInsight insight = cardInsights.get(row.getId());
        if (insight == null) return "";
        if (Boolean.FALSE.equals(insight.getValuable())) return "";
        if (insight.getValueScore() != null && insight.getValueScore() < 6.0d) return "";
        if (insight.getConfidence() != null && insight.getConfidence() < 0.35d) return "";
        String detail = cleanLlmDetailHighlight(insight.getDetailHighlight(), title);
        return detail == null ? "" : detail;
    }

    private static final Pattern HIGHLIGHT_MODEL_PATTERN = Pattern.compile(
            "(猛士\\s?M?817|猛士\\s?917|问界\\s?M[689](?:\\s?(?:Max\\+?|Ultra|EV|REEV|增程版|纯电版|加长版))?|"
                    + "坦克\\s?(?:300|400|500|700)(?:\\s?Hi4-T)?|方程豹\\s?(?:豹5|豹8|钛7)|豹\\s?(?:5|8)|"
                    + "仰望\\s?U(?:7|8L?|9)|小鹏\\s?GX|岚图泰山X8|极狐T1|腾势N8L|比亚迪大唐|"
                    + "奥迪A5L|宏光MINIEV|途观L\\s?ePro)");
    private static final Pattern HIGHLIGHT_NUMBER_PATTERN = Pattern.compile(
            "(?:至高|最高|售价|起售价|权益|补贴|优惠|订单|大定)?\\s*\\d+(?:\\.\\d+)?\\s*"
                    + "(?:万|万元|元|公里|km|KM|kWh|度|台|辆|%|折|期)"
                    + "(?:\\s*(?:0息|免息|权益|补贴|优惠|起|起售|大定|订单))?");
    private static final Pattern HIGHLIGHT_SIGNAL_PATTERN = Pattern.compile(
            "(华为[^，。；|]{0,10}(?:激光雷达|途灵|乾崑)|巨鲸电池平台3\\.0|天神之眼5\\.0|激光雷达|NOA|800V|"
                    + "智驾|辅助驾驶|云台车身控制|四驱|双电机|底盘|悬架|座舱|轴距|续航|电池|快充|"
                    + "置换补贴|增换购|0息|免息|订金膨胀|购车权益|限时权益|老车主权益|订单|大定|销量|交付|"
                    + "冠军|亚军|环塔|车展|试驾|品牌大使|用户共创|社群|召回|事故|自燃|维权|投诉|舆情)");

    private static String highlightImportant(String text) {
        if (text == null || text.isEmpty()) return "";
        List<HighlightSpan> candidates = new ArrayList<>();
        collectHighlightSpans(text, HIGHLIGHT_MODEL_PATTERN, candidates, 4);
        collectHighlightSpans(text, HIGHLIGHT_NUMBER_PATTERN, candidates, 4);
        collectHighlightSpans(text, HIGHLIGHT_SIGNAL_PATTERN, candidates, 6);
        if (candidates.isEmpty()) return safe(text);

        candidates.sort((a, b) -> {
            if (a.start != b.start) return Integer.compare(a.start, b.start);
            return Integer.compare((b.end - b.start), (a.end - a.start));
        });
        List<HighlightSpan> selected = new ArrayList<>();
        for (HighlightSpan candidate : candidates) {
            if (candidate.end <= candidate.start) continue;
            if (isInsideExistingMarkdown(text, candidate.start, candidate.end)) continue;
            boolean overlap = false;
            for (HighlightSpan existing : selected) {
                if (candidate.start < existing.end && candidate.end > existing.start) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;
            selected.add(candidate);
            if (selected.size() >= 8) break;
        }
        if (selected.isEmpty()) return safe(text);

        selected.sort(Comparator.comparingInt(s -> s.start));
        selected = mergeAdjacentHighlightSpans(text, selected);
        StringBuilder sb = new StringBuilder(text.length() + selected.size() * 4);
        int cursor = 0;
        for (HighlightSpan span : selected) {
            sb.append(text, cursor, span.start);
            sb.append("**").append(text, span.start, span.end).append("**");
            cursor = span.end;
        }
        sb.append(text.substring(cursor));
        return sb.toString();
    }

    private static void collectHighlightSpans(String text, Pattern pattern, List<HighlightSpan> out, int max) {
        if (text == null || text.isEmpty() || pattern == null || out == null || max <= 0) return;
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find() && count < max) {
            String match = matcher.group();
            if (match == null || match.trim().length() < 2) continue;
            out.add(new HighlightSpan(matcher.start(), matcher.end()));
            count++;
        }
    }

    private static boolean isInsideExistingMarkdown(String text, int start, int end) {
        if (text == null || start < 0 || end > text.length()) return false;
        int before = text.lastIndexOf("**", start);
        int afterBefore = before < 0 ? -1 : text.indexOf("**", before + 2);
        if (before >= 0 && afterBefore >= end) return true;
        return (start >= 2 && text.charAt(start - 1) == '*' && text.charAt(start - 2) == '*')
                || (end + 1 < text.length() && text.charAt(end) == '*' && text.charAt(end + 1) == '*');
    }

    private static List<HighlightSpan> mergeAdjacentHighlightSpans(String text, List<HighlightSpan> spans) {
        if (spans == null || spans.size() <= 1) return spans;
        List<HighlightSpan> merged = new ArrayList<>();
        HighlightSpan current = spans.get(0);
        for (int i = 1; i < spans.size(); i++) {
            HighlightSpan next = spans.get(i);
            String gap = text.substring(current.end, next.start);
            if (gap.isEmpty()) {
                current = new HighlightSpan(current.start, next.end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static class HighlightSpan {
        final int start;
        final int end;

        HighlightSpan(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static boolean hasInsightValueBeyondTitle(String point, String title) {
        String p = normalizeComparable(point);
        String t = normalizeComparable(title);
        if (p.isEmpty()) return false;
        boolean judgement = p.matches(".*(说明|意味着|形成|带来|有助于|强化|提升|压力|参考|观察|猛士|用户|市场|终端|转化|声量|心智|线索|策略|价格带|配置普及|圈层|流量|分流|锚点).*");
        boolean concrete = p.matches(".*(权益|优惠|补贴|置换|0息|免息|金融|保险|订单|大定|销量|交付|冠军|亚军|环塔|车展|试驾|代言|共创|智驾|激光雷达|电池|续航|底盘|四驱|座舱|空间).*");
        if (!judgement && !concrete) return false;
        if (t.isEmpty()) return true;
        return p.length() >= t.length() + 8 || judgement;
    }

    private static boolean isLowValueLlmPoint(String text) {
        String v = cleanDigestPoint(text);
        if (v.isEmpty()) return true;
        String compact = v.replaceAll("\\s+", "");
        if (compact.matches(".*(正式召开|正式举行|即将举行|正式亮相|正式发布|召开发布会|开启预售|公布结果|结果正式公布).*")
                && !compact.matches(".*(激光雷达|电池|平台|智驾|辅助驾驶|座舱|四驱|底盘|悬架|权益|补贴|置换|订单|大定|交付|用户|社群|共创|赛事|冠军|亚军).*")) {
            return true;
        }
        if (compact.matches(".*(多维度升级|配置升级|产品信息|持续传播|积极参与).*")) {
            return true;
        }
        boolean hasDecisionSignal = compact.matches(".*(市场|心智|压力|线索|转化|终端|用户|社群|共创|传播|声量|权益|价格锚点|场景|猛士|参考|对比|竞争|高端|家庭|越野|智驾|座舱|底盘|电池|订单).*");
        boolean hasSpecificFact = compact.matches(".*(\\d+|激光雷达|巨鲸|云台|电池|座椅|通风|按摩|四驱|双电机|续航|补贴|置换|环塔|T2\\.E|车展|代言|试驾).*");
        return !hasDecisionSignal && !hasSpecificFact;
    }

    private static String cleanLlmDetailHighlight(String detail, String title) {
        if (detail == null || detail.trim().isEmpty()) return "";
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : detail.split("[|｜；;，,、\\n]")) {
            String v = cleanDigestPoint(raw);
            if (v.isEmpty()) continue;
            if (isDuplicateSnippet(v, title, null)) continue;
            if (isLowValueLlmDetail(v)) continue;
            out.add(truncate(v, 34));
            if (out.size() >= 8) break;
        }
        return out.isEmpty() ? "" : String.join(" | ", out);
    }

    private static boolean isLowValueLlmDetail(String text) {
        String v = cleanDigestPoint(text);
        if (v.isEmpty()) return true;
        String compact = v.replaceAll("\\s+", "");
        if (compact.length() < 2) return true;
        if (compact.matches(".*(官方账号|直播频道|免责声明|扫码|小程序|平台|用户协议|隐私政策|网页链接).*")) return true;
        if (compact.matches(".*(提升品牌影响力|增强市场竞争力|形成传播触点|多维度升级|持续传播|值得关注|市场意义|猛士).*")) return true;
        if (compact.matches(".*(^|\\D)0{2,}元.*") || compact.matches("^\\d{1,2}$")) return true;
        if (compact.matches(".*(车手|领航员|DONGFENGMOTOR|GWMCCTV).*")
                && !compact.matches(".*(冠军|亚军|季军|T2\\.E|T2E|环塔|参赛|车队).*")) return true;
        return false;
    }

    private static String toCompleteDigestSentence(String text) {
        String v = cleanDigestPoint(text);
        if (v.isEmpty()) return "";
        v = truncateAtSentenceBoundary(v, 135);
        if (!v.matches(".*[。！？!?…]$")) v += "。";
        return highlightImportant(v);
    }

    private static String cleanDigestPoint(String text) {
        String v = normalizeContentSnippetText(text);
        if (v.isEmpty()) return "";
        v = v.replaceAll("【[^】]{1,30}】", "")
                .replaceAll("^\\s*(IT之家|网通社|快科技|太平洋汽车|新浪汽车|搜狐汽车|汽车之家|易车|懂车帝|盖世汽车|媒体)\\s*(资讯|快报|消息|报道称|报道)?\\s*", "")
                .replaceAll("^(并|且|同时|此外|另外|其中|报道称|提到|显示|有意思的是|据了解|据悉|目前|日前|今日|近日|消息)[:：,，\\s]*", "")
                .replaceAll("^[：:，,。；;、/／\\-\\s]+", "")
                .replaceAll("[：:，,。；;、/／\\-\\s]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (isPureDateTimePoint(v)) {
            return "";
        }
        if (v.length() <= 18 && v.matches(".*(频道|快报|资讯|消息|报道称|报道|新闻|栏目).*")) {
            return "";
        }
        return v;
    }

    private static boolean isLowValuePriceOrTimeOnly(String text) {
        String v = cleanDigestPoint(text);
        if (v.isEmpty()) return true;
        boolean priceOrTime = v.matches(".*(售价|价格|预售价|指导价|起售|上市价|\\d+\\.?\\d*\\s*万|\\d{1,2}月\\d{1,2}日|\\d{1,2}[:：]\\d{2}).*");
        if (!priceOrTime) return false;
        boolean hasValueDetail = v.matches(".*(权益|优惠|补贴|置换|金融|保险|首付|订金|定金|0息|低息|礼包|保养|质保|配置|智驾|座舱|续航|电池|四驱|底盘|悬架|订单|大定|交付|销量|用户|场景).*");
        return !hasValueDetail;
    }

    private static boolean hasDigestValue(String text, List<String> keywords) {
        String v = cleanDigestPoint(text);
        if (v.isEmpty()) return false;
        if (isPureDateTimePoint(v)) return false;

        boolean hasBusinessAction = v.matches(".*(上市|发布|预售|交付|到店|开售|开启|官宣|公布|推出|升级|焕新|改款|新增|标配|搭载|调整|下调|优惠|补贴|置换|报名|试驾|亮相|开幕|启幕|合作|大定|订单|销量|夺冠|冠军|亚军|完赛|上市指导价|预售价).*");
        boolean hasMarketObject = v.matches(".*(车型|新车|车主|用户|门店|展厅|车展|发布会|直播|活动|赛事|权益|价格|售价|金融|保险|配置|参数|版本|版|SUV|EV|PHEV|增程|纯电).*");
        boolean hasProductSpec = v.matches(".*(轴距|车身|尺寸|空间|大五座|座舱|座椅|通风|加热|按摩|智驾|辅助驾驶|激光雷达|四驱|双电机|底盘|云台|悬架|接近角|离去角|涉水|通过性|续航|电池|充电|高压平台|功率|扭矩).*");
        boolean hasCommercialMetric = v.matches(".*\\d+\\.?\\d*\\s*(万元|元|%|折|km|公里|mm|毫米|米|台|辆|万|kWh|V|天|小时).*");
        boolean hasContextualTime = v.matches(".*(\\d{1,2}月\\d{1,2}日|\\d{1,2}[:：]\\d{2}).*")
                && v.matches(".*(活动|发布会|直播|报名|截止|开启|上市|预售|交付|试驾|到店|时间|日期).*");

        int score = 0;
        if (containsAny(v, keywords)) score++;
        if (hasBusinessAction) score += 2;
        if (hasMarketObject) score++;
        if (hasProductSpec) score += 2;
        if (hasCommercialMetric && (hasBusinessAction || hasMarketObject || hasProductSpec)) score += 2;
        if (hasContextualTime) score++;
        return score >= 2;
    }

    private static boolean isPureDateTimePoint(String text) {
        if (text == null) return true;
        String v = text.replaceAll("\\s+", "").trim();
        if (v.isEmpty()) return true;
        return v.matches("^(\\d{4}年)?\\d{1,2}月\\d{1,2}日([上下]午)?\\d{0,2}([:：]\\d{2})?$")
                || v.matches("^\\d{1,2}[:：]\\d{2}(-\\d{1,2}[:：]\\d{2})?$")
                || v.matches("^[上下]午\\d{1,2}[:：]\\d{2}$");
    }

    private static List<String> extractCampaignValuePoints(String text, WebSearchNews row,
                                                           String title, String existingDetail,
                                                           String base, int maxPoints) {
        if (text == null || text.trim().isEmpty() || maxPoints <= 0) return Collections.emptyList();
        LinkedHashSet<String> points = new LinkedHashSet<>();
        LinkedHashMap<String, Integer> candidates = new LinkedHashMap<>();
        String normalized = normalizeOcrForDisplay(text);

        addCampaignRegexCandidate(candidates, normalized, "(?:[\\u4e00-\\u9fa5A-Za-z0-9]+车队)?[^，。；;]{0,20}(?:获|荣获|斩获|夺得|勇夺)[^，。；;]{0,36}(?:冠军|亚军|季军|完赛)", maxPoints);
        addCampaignRegexCandidate(candidates, normalized, "[\\u4e00-\\u9fa5A-Za-z0-9]{1,20}(?:成为|出任)[^，。；;]{0,24}品牌大使", maxPoints);
        addCampaignRegexCandidate(candidates, normalized, "[^，。；;]{0,24}(?:亮相|开启|启动|开幕|启幕|官宣|发布|公布|报名|招募|试驾|巡展|快闪|品鉴|体验|直播)[^，。；;]{0,36}", maxPoints);
        addCampaignRegexCandidate(candidates, normalized, "(?:活动名称|活动主题|主题|活动|时间|日期|报名截止|截止)[:：]?[\\u4e00-\\u9fa5A-Za-z0-9\\s~\\-—–至:：.]{4,40}", maxPoints);
        addCampaignRegexCandidate(candidates, normalized, "(?:地点|场地|展台|城市)[:：]?[\\u4e00-\\u9fa5A-Za-z0-9（）()\\s~\\-—–]{4,38}", maxPoints);
        addCampaignRegexCandidate(candidates, normalized, "[^，。；;]{0,18}(?:权益|福利|礼|抽奖|补贴|优惠|礼包|门票|名额)[^，。；;]{0,34}", maxPoints);

        java.util.regex.Matcher modelMatcher = java.util.regex.Pattern
                .compile("(?:参与车型|车型)[:：]?\\s*([\\u4e00-\\u9fa5A-Za-z0-9+\\-]{2,24})")
                .matcher(normalized);
        if (modelMatcher.find()) {
            String exactModel = modelMatcher.group(1);
            String action = normalized.matches(".*(赛事|车队|环塔|参赛|出征|拉力赛|T2\\.E).*") ? "参赛" : "参与活动";
            addCampaignCandidate(candidates, "车型" + exactModel + action);
        }

        String model = row == null ? "" : firstNonBlank(row.getModelMentioned(), row.getBrandName());
        if (model != null && !model.isEmpty()
                && normalized.contains(model) && normalized.matches(".*(赛事|车队|环塔|参赛|出征|拉力赛|T2\\.E).*")) {
            addCampaignCandidate(candidates, "车型" + model.split(",")[0].trim() + "参赛");
        }
        if (normalized.contains("环塔")) {
            addCampaignCandidate(candidates, "出征环塔拉力赛");
        }

        for (String raw : normalized.split("[\\n。；;！!？?，,、]")) {
            String clause = cleanCampaignValueClause(raw);
            if (clause.length() < 6 || clause.length() > 58) continue;
            if (isDuplicateSnippet(clause, title, existingDetail) || isDuplicateSnippet(clause, base, null)) continue;
            addCampaignCandidate(candidates, truncate(clause, 46));
        }

        List<Map.Entry<String, Integer>> ordered = new ArrayList<>(candidates.entrySet());
        ordered.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        for (Map.Entry<String, Integer> entry : ordered) {
            if (points.size() >= maxPoints) break;
            addUniqueCampaignPoint(points, entry.getKey());
        }
        return new ArrayList<>(points);
    }

    private static void addCampaignRegexCandidate(Map<String, Integer> candidates, String text, String regex, int maxPoints) {
        if (text == null || text.isEmpty() || candidates.size() >= maxPoints * 4) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        while (matcher.find() && candidates.size() < maxPoints * 4) {
            addCampaignCandidate(candidates, truncate(matcher.group(), 46));
        }
    }

    private static void addCampaignCandidate(Map<String, Integer> candidates, String point) {
        String v = cleanCampaignValueClause(point);
        if (v.isEmpty()) return;
        int score = campaignValueScore(v);
        if (score < 3) return;
        String comparable = normalizeComparable(v);
        if (comparable.isEmpty()) return;
        String replaceKey = null;
        for (Map.Entry<String, Integer> existing : candidates.entrySet()) {
            String e = normalizeComparable(existing.getKey());
            if (e.isEmpty()) continue;
            if (e.contains(comparable)) return;
            if (comparable.contains(e) && score >= existing.getValue()) {
                replaceKey = existing.getKey();
                break;
            }
        }
        if (replaceKey != null) candidates.remove(replaceKey);
        candidates.put(v, score);
    }

    private static void addCampaignRegexPoint(Set<String> points, String text, String regex, int maxPoints) {
        if (points.size() >= maxPoints || text == null || text.isEmpty()) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        while (matcher.find() && points.size() < maxPoints) {
            String point = cleanCampaignValueClause(matcher.group());
            if (point.length() >= 4 && hasCampaignValue(point)) {
                addUniqueCampaignPoint(points, truncate(point, 46));
            }
        }
    }

    private static void addUniqueCampaignPoint(Set<String> points, String point) {
        String v = cleanCampaignValueClause(point);
        if (v.isEmpty()) return;
        String comparable = normalizeComparable(v);
        for (String existing : points) {
            String e = normalizeComparable(existing);
            if (!e.isEmpty() && !comparable.isEmpty() && (e.contains(comparable) || comparable.contains(e))) {
                return;
            }
        }
        points.add(v);
    }

    private static String cleanCampaignValueClause(String text) {
        String v = cleanDigestPoint(text);
        if (v.isEmpty()) return "";
        v = v.replaceAll("(?i)DONGFENG\\s+MOTOR", "")
                .replaceAll("东风汽车", "")
                .replaceAll("扫码[^，。；;]{0,18}", "")
                .replaceAll("进入[^，。；;]{0,18}小程序", "")
                .replaceAll("(猛士心无畏敢征服|心无畏|敢征服|先为舟楫|为舟楫|后作先锋|行稳致远|乘风踏沙|勇攀新境)", "")
                .replaceAll("(车手|领航员)\\s*[\\u4e00-\\u9fa5]{2,4}", "")
                .replaceAll("[\\u4e00-\\u9fa5]{2,4}\\s*\\d+号车组(车手|领航员)", "")
                .replaceAll("(华为乾崑|宁德时代)(\\s|$)", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^[：:，,。；;、/／\\-\\s]+", "")
                .replaceAll("[：:，,。；;、/／\\-\\s]+$", "")
                .trim();
        if (v.isEmpty() || isCampaignNoise(v)) return "";
        return v;
    }

    private static boolean hasCampaignValue(String text) {
        return campaignValueScore(text) >= 3;
    }

    private static int campaignValueScore(String text) {
        String v = cleanCampaignValueClause(text);
        if (v.isEmpty()) return 0;
        if (isPureDateTimePoint(v)) return 0;
        boolean subject = v.matches(".*(品牌|车型|车队|车组|用户|车主|门店|展厅|经销商|媒体|KOL|明星|品牌大使|渠道|社群|粉丝).*");
        boolean action = v.matches(".*(发布|官宣|公布|开启|启动|亮相|招募|报名|预约|试驾|交付|上市|预售|合作|共创|投票|直播|探店|打卡|体验|挑战|传播|投放|联名|赞助|出征|参赛|参与|登陆|登场|巡展|快闪|品鉴|获|荣获|斩获|夺得|勇夺|完赛).*");
        boolean result = v.matches(".*(冠军|亚军|季军|奖|破|突破|达成|增长|热度|声量|曝光|订单|大定|销量|交付|转化|线索|到店|关注|口碑).*");
        boolean scene = v.matches(".*(赛事|赛段|环塔|拉力赛|车展|发布会|直播|活动|体验营|露营|营地|越野|试驾|门店|城市|展台|商圈|社群|巡展|快闪|公园).*");
        boolean resource = v.matches(".*(权益|福利|礼|抽奖|补贴|优惠|置换|金融|名额|礼包|门票|试驾礼|购车礼|保养|保险|积分).*");
        boolean metric = v.matches(".*(T2\\.E|SS\\d+|\\d+号车组|\\d{1,2}月\\d{1,2}日|\\d{1,2}[:：]\\d{2}|\\d+\\.?\\d*\\s*(万元|元|台|辆|天|小时|人|万|%)).*");
        boolean productOrBrand = v.matches(".*([A-Za-z]*\\d{1,3}[A-Za-z+\\-]*|M817|M9|M8|豹\\d|坦克\\d+|仰望U\\d|猛士|方程豹|问界|坦克|路虎|卫士|仰望).*");
        boolean marketDecision = v.matches(".*(线索|到店|转化|声量|热度|曝光|投放|渠道|权益|补贴|试驾|体验|车展|发布会|赛事|品牌大使|联名|合作|用户|车主|门店).*");

        int score = 0;
        if (subject) score++;
        if (action) score += 2;
        if (result) score += 2;
        if (scene) score++;
        if (resource) score += 2;
        if (metric && (action || result || scene || resource)) score += 2;
        if (productOrBrand && (action || result || scene || resource)) score++;
        if (marketDecision) score++;
        return score;
    }

    private static boolean isCampaignNoise(String text) {
        if (text == null) return true;
        String v = text.replaceAll("\\s+", " ").trim();
        if (v.isEmpty()) return true;
        if (v.matches("(?i).*(DONGFENG MOTOR|扫码|小程序|视频号|官方账号).*")) return true;
        if (v.matches(".*(先为舟楫|后作先锋|心无畏|敢征服|行稳致远|乘风踏沙|勇攀新境).*")) return true;
        if (v.matches(".*(车手|领航员).*") && !v.matches(".*(冠军|亚军|季军|完赛|车型|参赛).*")) return true;
        return v.length() <= 10 && !v.matches(".*(冠军|亚军|季军|权益|试驾|车展|发布会).*");
    }

    private static String truncateAtSentenceBoundary(String text, int maxLen) {
        if (text == null) return "";
        String v = text.trim();
        if (v.length() <= maxLen) return v;
        int cut = -1;
        String marks = "。！？!?；;";
        for (int i = Math.min(maxLen, v.length() - 1); i >= Math.max(30, maxLen - 50); i--) {
            if (marks.indexOf(v.charAt(i)) >= 0) {
                cut = i + 1;
                break;
            }
        }
        if (cut < 0) {
            for (int i = Math.min(maxLen, v.length() - 1); i >= Math.max(30, maxLen - 35); i--) {
                char c = v.charAt(i);
                if (c == '，' || c == ',' || c == '、') {
                    cut = i;
                    break;
                }
            }
        }
        if (cut < 0) cut = maxLen;
        return v.substring(0, Math.min(cut, v.length())).replaceAll("[，,、；;：:]+$", "") + "…";
    }

    private static List<String> snippetKeywords(String eventType) {
        if ("price_finance".equals(eventType)) {
            return Arrays.asList("售价", "价格", "万元", "限时", "优惠", "补贴", "置换", "首付",
                    "0息", "低息", "金融", "保险", "权益", "订金", "定金");
        }
        if ("campaign".equals(eventType)) {
            return Arrays.asList("活动", "试驾", "报名", "露营", "赛事", "挑战", "发布会", "直播",
                    "门店", "城市", "时间", "地点", "权益", "礼", "福利", "抽奖", "体验营", "用户共创");
        }
        return Arrays.asList("上市", "发布", "预售", "改款", "焕新", "升级", "配置", "轴距", "空间",
                "大五座", "座舱", "座椅", "智驾", "智能越野", "辅助驾驶", "四驱", "双电机",
                "底盘", "云台", "悬架", "接近角", "离去角", "涉水", "通过性", "续航", "电池", "充电");
    }

    private static String pickUsefulSnippetClause(String text, List<String> keywords, String title, String existingDetail) {
        String[] clauses = text.split("[\\n。；;！!？?，,、]");
        for (String raw : clauses) {
            String clause = normalizeContentSnippetText(raw);
            clause = cleanDigestPoint(clause);
            if (clause.length() < 10) continue;
            if (isNoisyOcrClause(clause)) continue;
            if (isDuplicateSnippet(clause, title, existingDetail)) continue;
            if (isLowValuePriceOrTimeOnly(clause) || isLowValueLlmPoint(clause)) continue;
            if (hasDigestValue(clause, keywords)) {
                return clause;
            }
        }
        for (String raw : clauses) {
            String clause = normalizeContentSnippetText(raw);
            clause = cleanDigestPoint(clause);
            if (clause.length() >= 12 && !isNoisyOcrClause(clause) && !isDuplicateSnippet(clause, title, existingDetail)) {
                if (isLowValuePriceOrTimeOnly(clause) || isLowValueLlmPoint(clause)) continue;
                return clause;
            }
        }
        return "";
    }

    private static String removeDuplicateSnippetPrefix(String text, String title, String existingDetail) {
        String v = text == null ? "" : text.trim();
        if (title != null && !title.trim().isEmpty()) {
            v = v.replace(title.trim(), "").trim();
        }
        if (existingDetail != null && !existingDetail.trim().isEmpty()) {
            v = v.replace(existingDetail.trim(), "").trim();
        }
        v = v.replaceAll("^[：:，,。；;、\\-\\s]+", "").trim();
        if (v.length() > 0 && !isNoisyOcrClause(v)) return v;
        return "";
    }

    private static String normalizeContentSnippetText(String text) {
        if (text == null) return "";
        String v = stripMarkedImageSummary(stripHashTags(text));
        v = v.replace("图含：", "")
                .replace("【图摘】", "")
                .replace("【政策】", "")
                .replaceAll("https?://\\S+", "")
                .replaceAll("[\\u4e00-\\u9fa5A-Za-z0-9·_-]{2,30}的微博(视频|正文|直播)", "")
                .replaceAll("(微博视频|微博正文|查看图片|展开全文)", "")
                .replaceAll("\\s*([，。；：、,.!?！？])\\s*", "$1")
                .replaceAll("[\\r\\t]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("([，,、；;：:])\\s*([。.!！?？])", "$2")
                .replaceAll("^[，,、；;：:。.!！?？\\s]+", "")
                .replaceAll("[，,、；;：:\\s]+$", "")
                .replaceAll("[/／]+\\s*(?=。|；|$)", "")
                .trim();
        return v;
    }

    private static boolean isDuplicateSnippet(String snippet, String title, String existingDetail) {
        String s = normalizeComparable(snippet);
        if (s.length() < 8) return false;
        String t = normalizeComparable(title);
        String d = normalizeComparable(existingDetail);
        return (!t.isEmpty() && (t.contains(s) || s.contains(t)))
                || (!d.isEmpty() && (d.contains(s) || s.contains(d)))
                || isNearDuplicateText(s, t)
                || isNearDuplicateText(s, d);
    }

    private static String normalizeComparable(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\s\\p{Punct}，。；：、！？（）【】《》“”‘’·|/\\-—_]+", "").trim();
    }

    private static boolean isNearDuplicateText(String a, String b) {
        if (a == null || b == null || a.length() < 8 || b.length() < 8) return false;
        String x = normalizeDuplicateComparable(a);
        String y = normalizeDuplicateComparable(b);
        if (x.length() < 8 || y.length() < 8) return false;
        if (x.contains(y) || y.contains(x)) return true;
        String shorter = x.length() <= y.length() ? x : y;
        String longer = x.length() <= y.length() ? y : x;
        if (shorter.length() > 34) return false;
        int matched = 0;
        for (int i = 0; i < shorter.length(); i++) {
            if (longer.indexOf(shorter.charAt(i)) >= 0) matched++;
        }
        return matched >= Math.max(8, (int) Math.ceil(shorter.length() * 0.82));
    }

    private static String normalizeDuplicateComparable(String text) {
        if (text == null) return "";
        return normalizeComparable(text)
                .replaceAll("(正式|即将|全新|一代|系列|新品|版本|车型|上市|发布|举行|起售|售价|售|价格|区间|为|的|了)", "")
                .replaceAll("(万元|元|万|起|至|到)", "")
                .trim();
    }

    private static LinkedHashMap<String, List<WebSearchNews>> groupProductEventsByModel(List<WebSearchNews> rows) {
        LinkedHashMap<String, List<WebSearchNews>> grouped = new LinkedHashMap<>();
        for (WebSearchNews row : rows) {
            String group = productBrandGroup(row);
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(row);
        }
        return orderProductGroups(grouped);
    }

    private static LinkedHashMap<String, List<WebSearchNews>> orderProductGroups(Map<String, List<WebSearchNews>> grouped) {
        LinkedHashMap<String, List<WebSearchNews>> ordered = new LinkedHashMap<>();
        List<String> preferred = Arrays.asList("猛士", "仰望", "路虎卫士", "坦克SUV", "方程豹", "问界AITO");
        for (String key : preferred) {
            List<WebSearchNews> rows = grouped.get(key);
            if (rows != null && !rows.isEmpty()) ordered.put(key, rows);
        }
        for (Map.Entry<String, List<WebSearchNews>> e : grouped.entrySet()) {
            if (!ordered.containsKey(e.getKey())) ordered.put(e.getKey(), e.getValue());
        }
        return ordered;
    }

    private static String productBrandGroup(WebSearchNews row) {
        if (row == null) return "其他产品动态";
        String text = combineNonBlank(row.getBrandName(), row.getModelMentioned(), row.getEventSummary(),
                row.getTitle(), row.getContent(), row.getImageOcrText());
        String compact = text.replaceAll("\\s+", "");
        if (compact.matches(".*(猛士|M817|M917|猛士917|猛士M817).*")) return "猛士";
        if (compact.matches(".*(仰望|U7|U8L|U8|U9).*")) return "仰望";
        if (compact.matches(".*(路虎|卫士|Defender|DEFENDER).*")) return "路虎卫士";
        if (compact.matches(".*(坦克|TANK|Tank).*")) return "坦克SUV";
        if (compact.matches(".*(方程豹|豹5|豹8|豹7|钛7).*")) return "方程豹";
        if (compact.matches(".*(问界|AITO|鸿蒙智行|问界M8|问界M9).*")) return "问界AITO";
        return fallbackProductGroup(row);
    }

    private static List<String> extractModels(WebSearchNews row) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        String mentioned = row == null ? null : row.getModelMentioned();
        if (mentioned != null && !mentioned.trim().isEmpty()) {
            for (String part : mentioned.split("[,，、/|]")) {
                String normalized = normalizeModelName(part);
                if (!normalized.isEmpty()) models.add(normalized);
            }
        }
        if (models.isEmpty() && row != null) {
            String text = combineNonBlank(row.getEventSummary(), row.getTitle(), row.getContent(), row.getImageOcrText());
            for (String candidate : knownModelCandidates()) {
                if (text.contains(candidate)) models.add(candidate);
            }
        }
        return new ArrayList<>(models);
    }

    private static String fallbackProductGroup(WebSearchNews row) {
        if (row != null && row.getBrandName() != null && !row.getBrandName().isEmpty()) {
            return row.getBrandName() + "产品动态";
        }
        return "其他产品动态";
    }

    private static String normalizeModelName(String model) {
        if (model == null) return "";
        String v = model.trim();
        if (v.isEmpty()) return "";
        v = v.replaceAll("^(车型|车系|#)", "");
        v = v.replaceAll("(系列|车型)$", "");
        v = v.replaceAll("\\s+", "");
        if ("M817".equalsIgnoreCase(v)) return "猛士M817";
        if ("917".equals(v) || "M917".equalsIgnoreCase(v)) return "猛士917";
        if ("豹5".equals(v)) return "方程豹豹5";
        if ("豹8".equals(v)) return "方程豹豹8";
        if ("卫士".equals(v)) return "路虎卫士";
        return v.trim();
    }

    private static List<String> knownModelCandidates() {
        return Arrays.asList(
                "猛士M817", "猛士917", "M817", "917",
                "仰望U9", "仰望U8L", "仰望U8", "仰望U7", "U9", "U8L", "U8", "U7",
                "坦克700", "坦克500", "坦克400",
                "方程豹豹8", "方程豹豹5", "方程豹钛7", "豹8", "豹5", "钛7",
                "问界M9", "问界M8", "路虎卫士", "卫士");
    }

    private static String buildProductSummary(String group, List<WebSearchNews> items) {
        List<String> points = collectProductSpecPoints(group, items, 8);
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (String point : points) {
            String c = summarizeProductPoint(point);
            if (!c.isEmpty()) categories.add(c);
            if (categories.size() >= 4) break;
        }
        if (!categories.isEmpty()) {
            String models = formatMentionedProductModels(items);
            return group + (models.isEmpty() ? "" : "（" + models + "）")
                    + "重点释放" + joinChinese(new ArrayList<>(categories)) + "。";
        }

        String action = buildProductActionSummary(items);
        if (!action.isEmpty()) return group + action + "。";
        return group + "出现产品发布/改款相关动态，建议关注后续官方配置披露。";
    }

    private static String buildProductSpecHighlight(String group, List<WebSearchNews> items) {
        List<String> points = collectProductSpecPoints(group, items, 8);
        if (points.isEmpty()) return "";
        List<String> out = new ArrayList<>();
        String models = formatMentionedProductModels(items);
        out.add(models.isEmpty() ? group : group + " " + models);
        out.addAll(points);
        return truncate(String.join(" | ", out), 160);
    }

    private static String formatMentionedProductModels(List<WebSearchNews> items) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (items != null) {
            for (WebSearchNews item : items) {
                for (String model : extractModels(item)) {
                    String normalized = normalizeModelName(model);
                    if (!normalized.isEmpty()) models.add(normalized);
                    if (models.size() >= 5) break;
                }
                if (models.size() >= 5) break;
            }
        }
        return models.isEmpty() ? "" : String.join("/", models);
    }

    private static List<String> collectProductSpecPoints(String model, List<WebSearchNews> items, int limit) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        if (items == null) return new ArrayList<>();
        for (WebSearchNews item : items) {
            String text = combineNonBlank(item.getImageOcrText(), item.getContent(),
                    removeOcrSuffix(item.getEventSummary()), item.getTitle());
            text = stripHashTags(text);
            for (String spec : extractProductSpecPoints(text)) {
                String cleaned = cleanProductSpecPoint(model, spec);
                if (cleaned.isEmpty()) continue;
                points.add(cleaned);
                if (points.size() >= limit) return new ArrayList<>(points);
            }
        }
        return new ArrayList<>(points);
    }

    private static String summarizeProductPoint(String point) {
        if (point == null) return "";
        if (point.matches(".*(智驾|智能越野|辅助驾驶|NOA|激光雷达|雷达|芯片|OTA|泊车).*")) return "智能越野/智驾辅助";
        if (point.matches(".*(轴距|车长|车宽|车高|尺寸|空间|大五座).*")) return "轴距/空间";
        if (point.matches(".*(座舱|座椅|通风|加热|按摩|大屏|HUD|音响|冰箱).*")) return "座舱舒适";
        if (point.matches(".*(四驱|双电机|电机|动力|功率|扭矩|发动机|增程).*")) return "动力/四驱";
        if (point.matches(".*(底盘|悬架|空悬|CDC|云台|差速锁|平台|架构).*")) return "底盘技术";
        if (point.matches(".*(接近角|离去角|涉水|离地|通过性|越野模式|穿越).*")) return "越野通过性";
        if (point.matches(".*(续航|电池|充电|快充|800V|kWh|度).*")) return "续航/补能";
        if (point.matches(".*(配置|标配|选装|升级|焕新|改款|新款).*")) return "配置升级";
        return "";
    }

    private static String joinChinese(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        if (values.size() == 1) return values.get(0);
        if (values.size() == 2) return values.get(0) + "和" + values.get(1);
        return String.join("、", values.subList(0, values.size() - 1))
                + "和" + values.get(values.size() - 1);
    }

    private static String buildProductActionSummary(List<WebSearchNews> items) {
        if (items == null) return "";
        for (WebSearchNews item : items) {
            String text = stripHashTags(combineNonBlank(item.getEventSummary(), item.getTitle(), item.getContent()));
            text = removeOcrSuffix(text);
            if (text.matches(".*(上市|发布|正式发布).*")) return "释放上市/发布信息";
            if (text.matches(".*(预售|开启预订|开启小订|开启大定).*")) return "释放预售/预订信息";
            if (text.matches(".*(改款|新款|年款|焕新|升级).*")) return "释放改款/配置升级信息";
            if (text.matches(".*(试驾|体验|首批车主|交付).*")) return "释放试驾体验和交付信息";
        }
        return "";
    }

    private static String combineNonBlank(String... values) {
        if (values == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (v == null || v.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append("。");
            sb.append(v.trim());
        }
        return sb.toString();
    }

    private static List<String> extractProductSpecPoints(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        String normalized = normalizeOcrForDisplay(text)
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> points = new LinkedHashSet<>();
        String[] clauses = normalized.split("[\\n。；;！!？?，,、/|]");
        for (String raw : clauses) {
            String clause = raw == null ? "" : raw.trim();
            if (clause.length() < 3) continue;
            if (isNoisyOcrClause(clause)) continue;
            if (!looksLikeProductSpec(clause)) continue;
            clause = clause.replaceAll("^(并|且|同时|其中|此外|另外|支持|搭载|采用|配备)", "");
            points.add(truncate(clause, 38));
            if (points.size() >= 8) break;
        }

        if (points.isEmpty()) {
            addRegexPoint(points, normalized, "(轴距[^，。；;|/]{0,18}|[0-9]+\\.?[0-9]*\\s*(mm|毫米|米|m)级?轴距|轴距[0-9]+\\.?[0-9]*\\s*(mm|毫米|米|m))");
            addRegexPoint(points, normalized, "(前后双电机四驱|双电机四驱|电机四驱|四驱[^，。；;|/]{0,16})");
            addRegexPoint(points, normalized, "(智能越野辅助|辅助驾驶[^，。；;|/]{0,16}|智驾[^，。；;|/]{0,16})");
            addRegexPoint(points, normalized, "(云台车身控制|底盘[^，。；;|/]{0,18}|悬架[^，。；;|/]{0,18})");
            addRegexPoint(points, normalized, "(大五座布局|座椅[^，。；;|/]{0,24}|座舱[^，。；;|/]{0,18})");
            addRegexPoint(points, normalized, "(接近角[^，。；;|/]{0,12}|离去角[^，。；;|/]{0,12}|涉水[^，。；;|/]{0,12}|通过性[^，。；;|/]{0,12})");
            addRegexPoint(points, normalized, "(续航[^，。；;|/]{0,18}|[0-9]+\\s*(km|公里)[^，。；;|/]{0,12})");
        }
        return new ArrayList<>(points);
    }

    private static boolean looksLikeProductSpec(String clause) {
        String[] keywords = {
                "轴距", "空间", "大五座", "车长", "车宽", "车高", "尺寸", "离地间隙", "接近角", "离去角", "涉水", "通过性",
                "功率", "扭矩", "马力", "动力", "电机", "发动机", "增程", "四驱",
                "续航", "电池", "容量", "充电", "800V", "快充",
                "智驾", "智能越野辅助", "NOA", "辅助驾驶", "激光雷达", "雷达", "芯片", "座舱", "座椅", "通风", "加热", "按摩", "大屏", "HUD",
                "悬架", "空悬", "CDC", "差速锁", "底盘", "云台车身控制", "平台", "架构",
                "改款", "新款", "焕新", "升级", "配置", "标配", "选装"
        };
        for (String keyword : keywords) {
            if (clause.contains(keyword)) return true;
        }
        return clause.matches(".*\\d+\\.?\\d*\\s*(mm|毫米|米|m|km|公里|kW|千瓦|Ps|马力|N·m|牛·米|牛米|kWh|度|V).*");
    }

    private static void addRegexPoint(Set<String> points, String text, String regex) {
        if (points.size() >= 8) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (matcher.find()) {
            String point = matcher.group();
            if (!isNoisyOcrClause(point)) points.add(truncate(point, 38));
        }
    }

    private static String cleanProductSpecPoint(String model, String point) {
        if (point == null) return "";
        String v = stripHashTags(point)
                .replace("图含：", "")
                .replace("【图摘】", "")
                .replace("【政策】", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (model != null && !model.isEmpty()) {
            v = v.replace(model, "").trim();
        }
        v = v.replaceAll("^(：|:|，|,|。|、|\\|)+", "").trim();
        v = v.replaceAll("(等产品信息|产品信息|配置参数|具体信息)$", "").trim();
        if (v.length() < 2 || isNoisyOcrClause(v)) return "";
        return truncate(v, 38);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static String renderStructuredBriefing(String briefing) {
        String normalized = briefing == null ? "" : briefing.trim()
                .replace("\\n", "\n")
                .replace("\r", "")
                .replaceAll("\\n{2,}", "\n");
        if (normalized.isEmpty()) return "";

        String dynamic = extractBriefingSection(normalized, "动态总结");
        String risk = extractBriefingSection(normalized, "风险提示");
        String strategy = extractBriefingSection(normalized, "应对策略");
        if (dynamic.isEmpty() && risk.isEmpty() && strategy.isEmpty()) {
            return truncateAtSentenceBoundary(normalized.replaceAll("\\s+", " "), 180);
        }

        StringBuilder sb = new StringBuilder();
        appendBriefingLine(sb, "📌", "动态", dynamic);
        appendBriefingLine(sb, "⚠️", "风险", risk);
        appendBriefingLine(sb, "🎯", "应对", strategy);
        return sb.toString().trim();
    }

    private static String extractBriefingSection(String text, String label) {
        String[] labels = {"动态总结", "风险提示", "应对策略"};
        int start = indexOfBriefingLabel(text, label);
        if (start < 0) return "";
        int colon = text.indexOf('：', start);
        if (colon < 0) colon = text.indexOf(':', start);
        if (colon < 0) return "";

        int end = text.length();
        for (String next : labels) {
            if (next.equals(label)) continue;
            int idx = indexOfBriefingLabel(text, next);
            if (idx > colon && idx < end) end = idx;
        }
        return text.substring(colon + 1, end).trim();
    }

    private static int indexOfBriefingLabel(String text, String label) {
        int plain = text.indexOf(label + "：");
        if (plain >= 0) return plain;
        plain = text.indexOf(label + ":");
        if (plain >= 0) return plain;
        int bold = text.indexOf("**" + label + "：**");
        if (bold >= 0) return bold;
        return text.indexOf("**" + label + ":**");
    }

    private static void appendBriefingLine(StringBuilder sb, String icon, String label, String content) {
        if (content == null || content.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append("\n\n");
        String compact = truncateAtSentenceBoundary(content.replaceAll("\\s+", " ").trim(), 78);
        sb.append(icon).append(" **").append(label).append("：** ")
          .append(highlightImportant(safe(compact)));
    }

    private static List<String> splitBriefingItems(String content) {
        String cleaned = content == null ? "" : content
                .replaceAll("^\\*\\*", "")
                .replaceAll("\\*\\*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) return Collections.emptyList();

        String[] parts = cleaned.split("[；;。]");
        List<String> items = cleanBriefingParts(parts);
        if (items.size() <= 1 && cleaned.length() > 55) {
            items = cleanBriefingParts(cleaned.split("[，,]"));
        }
        if (items.isEmpty()) items = Collections.singletonList(cleaned);
        if (items.size() > 4) return items.subList(0, 4);
        return items;
    }

    private static List<String> cleanBriefingParts(String[] parts) {
        List<String> items = new ArrayList<>();
        if (parts == null) return items;
        for (String part : parts) {
            String v = part == null ? "" : part.trim();
            v = v.replaceAll("^[-•]\\s*", "");
            if (v.isEmpty()) continue;
            items.add(v);
        }
        return items;
    }

    private static String removeOcrSuffix(String text) {
        if (text == null) return "";
        int idx = text.indexOf("图含：");
        if (idx > 0) return text.substring(0, idx).trim();
        return text.trim();
    }

    private static String stripHashTags(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("#[^#]{1,40}#", "").replaceAll("\\s+", " ").trim();
    }

    /**
     * v8+ 事件条目正文（不含板块标题，供 collapsible_panel 使用）。
     * 每条排版（条目之间无空行）：
     *   <序号> 主标题（粗体）　查看 →
     *   　🅾️ 微博官号 · `品牌` · 4h 前
     *   　🔍 xxx                        ← 优先展示 image_ocr_text 原文细节，旧「图含」摘要仅兜底
     */
    private String renderEventBody(String eventType, List<WebSearchNews> rows,
                                   Map<Long, CardInsight> cardInsights) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            WebSearchNews x = rows.get(i);
            String full = x.getEventSummary();
            if (full == null || full.isEmpty()) full = x.getTitle();
            if (full == null) full = "";

            // 拆「图含：」前后两部分（OCR 增强摘要）
            String mainTitle;
            String ocrPart = null;
            int splitIdx = full.indexOf("图含：");
            if (splitIdx > 0) {
                String pre = full.substring(0, splitIdx).trim();
                if (pre.endsWith("/")) pre = pre.substring(0, pre.length() - 1).trim();
                mainTitle = pre;
            } else {
                mainTitle = full;
            }
            mainTitle = truncate(mainTitle, MAX_SUMMARY + 20);
            ocrPart = buildLlmDetailHighlight(cardInsights, x, mainTitle);
            String contentSnippet = buildReadableContentSnippet(eventType, x, mainTitle, ocrPart, cardInsights);
            if (isDuplicateSnippet(contentSnippet, mainTitle, null)) {
                contentSnippet = "";
            }
            if (isDuplicateSnippet(ocrPart, mainTitle, contentSnippet)) {
                ocrPart = "";
            }

            int estLineBytes = mainTitle.length() * 3 + 150
                    + (contentSnippet == null ? 0 : Math.min(contentSnippet.length(), 90) * 3)
                    + (ocrPart == null ? 0 : Math.min(ocrPart.length(), 80) * 3);
            if (sb.length() + estLineBytes > SECTION_BYTE_LIMIT) break;

            // 第一行：序号 + 主标题（粗体）+ 查看链接
            sb.append("**").append(i + 1).append(".** ");
            Integer imp = x.getEventImportance();
            if (imp != null && imp >= 8) sb.append("⭐ ");
            sb.append("**").append(safe(mainTitle)).append("**");
            String url = x.getUrl();
            if (url != null && !url.isEmpty()) {
                sb.append("　[<font color='blue'>查看原文 →</font>](").append(url).append(")");
            }
            sb.append("\n");

            // 第二行：来源 + 品牌 + 相对时间（灰字）
            sb.append("　<font color='grey'>");
            String src = x.getSourceTool();
            if (src != null && src.endsWith("_official")) {
                sb.append("🅾️ ").append(sourceLabel(src));
            } else {
                sb.append(sourceLabel(src));
            }
            if (x.getBrandName() != null && !x.getBrandName().isEmpty()) {
                sb.append(" · `").append(x.getBrandName()).append("`");
            }
            sb.append(" · ").append(formatRelativeTime(x.getAddTs()));
            sb.append("</font>");

            // 第三行（可选）：正文摘录，让微博图文和网页新闻不只露出标题
            if (contentSnippet != null && !contentSnippet.isEmpty()) {
                sb.append("\n　　要点：").append(safe(contentSnippet));
            }

            // 第四行（可选）：OCR 提取的政策摘要
            if (ocrPart != null && !ocrPart.isEmpty()) {
                sb.append("\n　<font color='").append(COLOR_BRAND).append("'>🔍 ")
                  .append(highlightImportant(ocrPart)).append("</font>");
            }
            // 条目之间用单个换行，避免大段空白
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String buildOcrDetail(WebSearchNews row) {
        if (row == null || row.getImageOcrText() == null || row.getImageOcrText().trim().isEmpty()) return null;
        return buildOcrHighlight("price_finance", row);
    }

    private static String buildOcrHighlight(String eventType, WebSearchNews row) {
        if (row == null || row.getImageOcrText() == null || row.getImageOcrText().trim().isEmpty()) return null;
        String ocr = normalizeOcrText(row.getImageOcrText());
        String ocrBody = stripMarkedImageSummary(ocr);

        List<String> points;
        if ("campaign".equals(eventType)) {
            return buildCampaignOcrHighlight(row, ocr, ocrBody);
        } else if ("price_finance".equals(eventType)) {
            points = extractFinancePolicyPoints(ocrBody);
            if (!points.isEmpty()) return truncate(String.join("；", points), 120);

            String marked = extractMarkedImageSummary(ocr);
            String markedCleaned = compactUsefulOcrText(eventType, marked, 120);
            return markedCleaned.isEmpty() ? null : markedCleaned;
        } else {
            points = extractProductSpecPoints(ocrBody);
        }
        if (points.isEmpty()) points = extractProductSpecPoints(ocrBody);
        if (!points.isEmpty()) return truncate(String.join("；", points), 120);

        String marked = extractMarkedImageSummary(ocr);
        String markedCleaned = compactUsefulOcrText(eventType, marked, 120);
        if (!markedCleaned.isEmpty() && !looksGenericOcrSummary(markedCleaned)) return markedCleaned;
        return truncate(firstUsefulOcrLine(ocrBody), 120);
    }

    private static String buildCampaignOcrHighlight(WebSearchNews row, String ocr, String ocrBody) {
        String marked = extractMarkedImageSummary(ocr);
        String text = marked == null || marked.isEmpty()
                ? combineNonBlank(ocrBody, row == null ? null : row.getEventSummary(),
                        row == null ? null : row.getContent(), row == null ? null : row.getTitle())
                : marked;
        List<String> points = extractCampaignValuePoints(text, row,
                row == null ? null : row.getTitle(),
                null,
                null,
                3);
        if (points.isEmpty()) return null;
        return truncate(String.join("；", points), 120);
    }

    private static String buildGroupOcrHighlight(String eventType, List<WebSearchNews> rows) {
        if (rows == null || rows.isEmpty()) return null;
        LinkedHashSet<String> points = new LinkedHashSet<>();
        for (WebSearchNews row : rows) {
            String part = buildOcrHighlight(eventType, row);
            if (part == null || part.isEmpty()) continue;
            for (String p : part.split("[；;]")) {
                String v = p == null ? "" : p.trim();
                if (!v.isEmpty()) points.add(v);
                if (points.size() >= 4) break;
            }
            if (points.size() >= 4) break;
        }
        return points.isEmpty() ? null : truncate(String.join("；", points), 140);
    }

    private static List<String> extractKeywordPoints(String text, List<String> keywords) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> points = new LinkedHashSet<>();
        String normalized = normalizeOcrForDisplay(text);
        String[] clauses = normalized.split("[\\n。；;！!？?，,、]");
        for (String raw : clauses) {
            String clause = raw == null ? "" : raw.trim();
            if (clause.length() < 3) continue;
            if (isNoisyOcrClause(clause)) continue;
            boolean hit = false;
            for (String keyword : keywords) {
                if (clause.contains(keyword)) {
                    hit = true;
                    break;
                }
            }
            if (!hit && !clause.matches(".*(\\d+\\.?\\d*\\s*(万元|元|%|折|天|月|日|h|小时|km|公里)|\\d{1,2}[:：]\\d{2}).*")) continue;
            points.add(truncate(clause, 34));
            if (points.size() >= 3) break;
        }
        return new ArrayList<>(points);
    }

    private static List<String> extractFinancePolicyPoints(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> keywords = strictFinancePolicyKeywords();
        LinkedHashSet<String> points = new LinkedHashSet<>();
        String normalized = normalizeOcrForDisplay(text);
        for (String raw : normalized.split("[\\n。；;！!？?，,、]")) {
            String clause = raw == null ? "" : raw.trim();
            if (clause.length() < 3) continue;
            if (isNoisyOcrClause(clause)) continue;
            if (isBrokenFinanceAmountClause(clause)) continue;
            if (!containsAny(clause, keywords)) continue;
            if (clause.matches(".*权益截止时间.*")
                    && !clause.matches(".*(至高|最高|价值|选配金|补贴|礼包|套餐|服务|尾款|减免|\\d+\\s*(元|万元)).*")) {
                continue;
            }
            if (clause.matches(".*(售价|指导价|预售价|起售价|万元).*")
                    && !clause.matches(".*(权益|优惠|补贴|置换|0息|零息|免息|低息|金融|保险|首付|订金|定金|膨胀|抵扣|减免|保养|质保).*")) {
                continue;
            }
            points.add(truncate(clause, 42));
            if (points.size() >= 3) break;
        }
        return new ArrayList<>(points);
    }

    private static List<String> strictFinancePolicyKeywords() {
        return Arrays.asList("权益", "优惠", "补贴", "置换", "增换购", "首付",
                "0息", "零息", "免息", "低息", "金融", "贷款", "保险",
                "订金", "定金", "膨胀", "抵扣", "减免", "购车礼", "保养", "质保",
                "选配金", "智驾", "礼包", "套餐", "无忧服务", "尾款", "臻选包", "甄选包");
    }

    private static boolean isBrokenFinanceAmountClause(String clause) {
        if (clause == null) return true;
        String v = clause.replaceAll("\\s+", "");
        if (v.matches(".*(^|\\D)0{2,}元.*")) return true;
        if (v.matches("^\\d{1,2}$")) return true;
        return v.matches(".*限时优惠价\\d{1,2}$");
    }

    private static String normalizeOcrText(String text) {
        if (text == null) return "";
        return normalizeNumericAmountCommas(text)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalizeOcrForDisplay(String text) {
        if (text == null) return "";
        return normalizeNumericAmountCommas(text)
                .replace("\r", "")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\s*([，。；：、,.!?！？])\\s*", "$1")
                .trim();
    }

    private static String normalizeNumericAmountCommas(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replaceAll("(?<=\\d),(?=\\d{3}(?:\\D|$))", "");
    }

    private static String extractMarkedImageSummary(String text) {
        if (text == null || text.isEmpty()) return "";
        for (String marker : Arrays.asList("【图摘】", "【政策】")) {
            int idx = text.indexOf(marker);
            if (idx >= 0) return text.substring(idx + marker.length()).trim();
        }
        return "";
    }

    private static String stripMarkedImageSummary(String text) {
        if (text == null || text.isEmpty()) return "";
        int cut = text.length();
        for (String marker : Arrays.asList("【图摘】", "【政策】")) {
            int idx = text.indexOf(marker);
            if (idx >= 0 && idx < cut) cut = idx;
        }
        return text.substring(0, cut).trim();
    }

    private static boolean looksGenericOcrSummary(String text) {
        if (text == null || text.isEmpty()) return true;
        String[] generic = {"等产品信息", "价格金融信息", "活动机制", "产品信息", "配置参数", "权益信息", "具体信息"};
        for (String g : generic) {
            if (text.contains(g)) return true;
        }
        return !text.matches(".*\\d+.*") && text.length() < 18;
    }

    private static String firstUsefulOcrLine(String text) {
        if (text == null || text.isEmpty()) return "";
        String cleaned = stripMarkedImageSummary(text).trim();
        String[] parts = cleaned.split("[\\n。；;！!？?，,、]");
        for (String part : parts) {
            String v = part == null ? "" : part.trim();
            if (v.length() >= 4 && !isNoisyOcrClause(v)) return v;
        }
        return isNoisyOcrClause(cleaned) ? "" : cleaned;
    }

    private static String cleanOcrPartForDisplay(String eventType, WebSearchNews row, String raw) {
        if ("campaign".equals(eventType)) {
            String highlighted = buildOcrHighlight(eventType, row);
            return highlighted == null ? "" : highlighted;
        }
        String highlighted = buildOcrHighlight(eventType, row);
        if (highlighted != null && !highlighted.isEmpty()) return highlighted;
        return compactUsefulOcrText(eventType, raw, 120);
    }

    private static String compactUsefulOcrText(String eventType, String text, int maxLen) {
        if (text == null || text.trim().isEmpty()) return "";
        if ("price_finance".equals(eventType)) {
            List<String> points = extractFinancePolicyPoints(text);
            return points.isEmpty() ? "" : truncate(String.join("；", points), maxLen);
        }
        List<String> keywords = "price_finance".equals(eventType)
                ? Arrays.asList("售价", "价格", "万元", "限时", "优惠", "补贴", "置换", "首付",
                        "0息", "低息", "金融", "保险", "权益", "订金", "定金")
                : "campaign".equals(eventType)
                ? Arrays.asList("活动", "试驾", "报名", "露营", "赛事", "挑战", "发布会", "直播",
                        "门店", "城市", "时间", "地点", "权益", "礼", "福利", "抽奖")
                : Arrays.asList("轴距", "续航", "电池", "电机", "功率", "扭矩", "座舱", "智驾",
                        "四驱", "底盘", "悬架", "离地", "涉水", "通过性", "OTA", "配置");
        List<String> points = extractKeywordPoints(text, keywords);
        if (!points.isEmpty()) return truncate(String.join("；", points), maxLen);

        LinkedHashSet<String> useful = new LinkedHashSet<>();
        String normalized = normalizeOcrForDisplay(text);
        for (String raw : normalized.split("[\\n。；;！!？?，,、]")) {
            String clause = raw == null ? "" : raw.trim();
            if (clause.length() < 4 || isNoisyOcrClause(clause)) continue;
            if (clause.matches(".*(\\d+\\.?\\d*\\s*(万元|元|%|折|天|月|日|h|小时|km|公里)|\\d{1,2}[:：]\\d{2}).*")
                    || containsAny(clause, keywords)) {
                useful.add(truncate(clause, 34));
            }
            if (useful.size() >= 3) break;
        }
        return useful.isEmpty() ? "" : truncate(String.join("；", useful), maxLen);
    }

    private static boolean containsAny(String text, List<String> keywords) {
        if (text == null || keywords == null) return false;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) return true;
        }
        return false;
    }

    private static boolean isNoisyOcrClause(String text) {
        if (text == null) return true;
        String v = text.replaceAll("\\s+", " ").trim();
        if (v.isEmpty()) return true;
        if (v.matches("^[\\d\\s:：\\-—–~至]+$")) return true;

        String[] hardNoise = {
                "车型图片信息", "可能与最终量产车型", "请以实际销售车型为准", "排名不分先后",
                "直播间实时活动", "实时活动和规则为准", "以上第三方网络平台", "第三方网络平台",
                "官方直播平台", "看车·买车·用车·换车", "看车-买车-用车-换车",
                "部分内容由AI辅助生成", "内容由AI辅助生成", "AI辅助生成"
        };
        for (String noise : hardNoise) {
            if (v.contains(noise)) return true;
        }

        boolean hasAction = v.matches(".*(发布|上市|预售|交付|试驾|报名|开启|开幕|启幕|挑战|权益|售价|优惠|补贴|置换|金融|配置|轴距|空间|大五座|座舱|座椅|通风|加热|按摩|续航|电池|智驾|辅助驾驶|四驱|双电机|底盘|云台|悬架|接近角|离去角|涉水|通过性|OTA|直播|发布会).*");
        boolean hasNumber = v.matches(".*(\\d+\\.?\\d*\\s*(万元|元|%|折|天|月|日|h|小时|km|公里)|\\d{1,2}[:：]\\d{2}).*");
        if (hasAction || hasNumber) return false;

        if (v.length() <= 14 && v.matches(".*(账号|视频号|汽车之家|易车|华为|鸿蒙智行|AITO|终端|商城|视频|抖音号|官方).*")) {
            return true;
        }
        return v.length() <= 8 && !v.matches(".*[A-Za-z0-9].*");
    }

    /**
     * v8+: 按对标车型最新金融政策板块（不限时间窗，每车型一行）。
     * 按 vs_self_model 分组展示，类似销量板块布局。
     */
    private String renderFinanceByModelSection(Map<String, List<WebSearchNews>> financeByVs,
                                                 Map<String, String> modelToVsSelf) {
        if (financeByVs == null || financeByVs.isEmpty()) return null;
        int total = financeByVs.values().stream().mapToInt(List::size).sum();
        if (total == 0) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("**🎯 按对标车型最新金融政策**　")
          .append("<font color='grey'>").append(total).append(" 款车型有数据 · 不限时间窗</font>\n");

        for (Map.Entry<String, List<WebSearchNews>> e : financeByVs.entrySet()) {
            String selfModel = e.getKey();
            List<WebSearchNews> rows = e.getValue();
            if (rows.isEmpty()) continue;

            sb.append("\n**🔸 vs ").append(safe(selfModel)).append("**\n");
            for (WebSearchNews x : rows) {
                if (sb.length() > SECTION_BYTE_LIMIT) break;
                String model = x.getModelMentioned() == null ? "" : x.getModelMentioned().split(",")[0].trim();
                boolean isSelf = selfModel.equals(model);
                String summary = x.getEventSummary();
                if (summary == null || summary.isEmpty()) summary = truncate(x.getTitle(), MAX_TITLE);
                else summary = truncate(summary, MAX_SUMMARY + 50);
                String ocrHighlight = buildOcrHighlight("price_finance", x);
                if (ocrHighlight != null && !ocrHighlight.isEmpty() && !summary.contains(ocrHighlight)) {
                    summary = summary + " / " + ocrHighlight;
                }

                sb.append("- ");
                if (isSelf) sb.append("🔹 **").append(safe(model)).append("**");
                else sb.append("`").append(safe(model)).append("`");
                sb.append("　🅾️ ");
                Integer imp = x.getEventImportance();
                if (imp != null && imp >= 8) sb.append("⭐ ");

                String url = x.getUrl();
                if (url != null && !url.isEmpty()) {
                    sb.append("[").append(safe(summary)).append("](").append(url).append(")");
                } else {
                    sb.append(safe(summary));
                }
                sb.append("\n　　<font color='grey'>来源：").append(sourceLabel(x.getSourceTool()))
                  .append(" · ").append(formatPublishTime(x.getAddTs())).append("</font>\n");
            }
        }
        return sb.toString();
    }

    /**
     * v8 销量板块：仅盖世结构化数据，按对标关系（vs_self_model）分组成多张表。
     * 每个 self 一张表 — 第一行高亮本品，其余按销量 DESC。
     * @return 板块 markdown；若无数据返回空状态友好提示
     */
    private String renderSalesSection(Map<String, List<GasgooSalesRecord>> gasgooSalesByVs) {
        int total = gasgooSalesByVs == null ? 0
                : gasgooSalesByVs.values().stream().mapToInt(List::size).sum();
        if (total == 0) {
            return "**" + EVENT_TYPE_LABEL.get("sales_milestone") + "**　<font color='grey'>0 条</font>\n"
                    + "<font color='grey'>暂无盖世销量数据。前往「车系配置」补 gasgoo_series_id 后触发抓取。</font>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(EVENT_TYPE_LABEL.get("sales_milestone")).append("**　")
          .append("<font color='grey'>盖世汽车 · ").append(total).append(" 款车型</font>\n");

        // 抓取时间统一展示（所有记录通常同批抓取，取第一条的 crawl_ts）
        Long anyCrawlTs = gasgooSalesByVs.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.getCrawlTs() != null)
                .map(GasgooSalesRecord::getCrawlTs)
                .findFirst().orElse(null);
        if (anyCrawlTs != null) {
            sb.append("<font color='grey'>抓取于 ").append(formatPublishTime(anyCrawlTs)).append("</font>\n");
        }

        // 每个对标分组一张表
        for (Map.Entry<String, List<GasgooSalesRecord>> e : gasgooSalesByVs.entrySet()) {
            String selfModel = e.getKey();
            List<GasgooSalesRecord> rows = e.getValue();
            if (rows.isEmpty()) continue;

            // v8+ 算本品销量 + 竞品最高，用于颜色对比
            Integer selfSales = null;
            int competitorMax = 0;
            for (GasgooSalesRecord r : rows) {
                Integer s = r.getSalesCount();
                if (s == null) continue;
                if (selfModel.equals(r.getModelName())) selfSales = s;
                else if (s > competitorMax) competitorMax = s;
            }

            sb.append("\n**🔸 vs ").append(safe(selfModel)).append("**");
            // v8+ 销量对比警示
            if (selfSales != null && competitorMax > selfSales * 2) {
                sb.append("　<font color='").append(COLOR_BRAND).append("'>⚠ 本品月销显著低于竞品</font>");
            }
            sb.append("\n| 车型 | 月销量 | 本年累计 |\n");
            sb.append("|---|---:|---:|\n");
            for (GasgooSalesRecord r : rows) {
                if (sb.length() > SECTION_BYTE_LIMIT) break;
                boolean isSelf = selfModel.equals(r.getModelName());
                String name = safe(r.getModelName());
                String monthDisp = r.getPeriodYear() + "-" + String.format("%02d", r.getPeriodMonth());
                Integer sales = r.getSalesCount();
                boolean isTopCompetitor = !isSelf && sales != null && sales == competitorMax && competitorMax > 0;

                sb.append("| ");
                if (isSelf) sb.append("🔹 **").append(name).append("**");
                else sb.append(name);

                // v8+ 销量数字颜色：本品红色（强提示）；竞品最高绿色（威胁）；其他默认
                String salesColor = isSelf ? COLOR_BRAND : (isTopCompetitor ? COLOR_THREAT : null);
                String salesStr = formatNum(sales);
                if (salesColor != null) {
                    sb.append(" | <font color='").append(salesColor).append("'>**")
                      .append(salesStr).append("**</font> ");
                } else {
                    sb.append(" | **").append(salesStr).append("** ");
                }
                sb.append("<font color='grey'>").append(monthDisp).append("</font>");
                sb.append(" | ").append(formatNum(r.getYtdCount()));
                String url = r.getSourceUrl();
                if (url != null && !url.isEmpty()) {
                    sb.append(" [↗](").append(url).append(")");
                }
                sb.append(" |\n");
            }
        }
        return sb.toString();
    }

    /** 全市场热门车型/事件，不受对标品牌白名单限制。 */
    private String renderMarketHotSection(List<WebSearchNews> rows, Map<Long, CardInsight> cardInsights) {
        if (rows == null || rows.isEmpty()) return "";
        List<WebSearchNews> candidates = prepareMarketHotItems(rows);
        if (candidates.isEmpty()) return "";

        LinkedHashMap<WebSearchNews, String> display = new LinkedHashMap<>();
        for (WebSearchNews x : candidates) {
            String title = marketHotTitle(x);
            String contentSnippet = buildMarketHotInsightSnippet(x, title, "", cardInsights);
            if (contentSnippet.isEmpty()) continue;
            display.put(x, contentSnippet);
            if (display.size() >= 3) break;
        }
        if (display.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("**🔥 市场近期热点**　<font color='grey'>")
          .append(display.size()).append(" 条</font>\n");

        int i = 0;
        for (Map.Entry<WebSearchNews, String> entry : display.entrySet()) {
            if (sb.length() > SECTION_BYTE_LIMIT) break;
            WebSearchNews x = entry.getKey();
            String title = marketHotTitle(x);
            sb.append("\n").append(++i).append(". ");
            Integer imp = x.getEventImportance();
            if (imp != null && imp >= 8) sb.append("⭐ ");
            String url = x.getUrl();
            if (url != null && !url.isEmpty()) {
                sb.append("[").append(safe(title)).append("](").append(url).append(")");
            } else {
                sb.append(safe(title));
            }
            sb.append("\n　<font color='grey'>")
              .append(safe(marketHotSourceName(x)))
              .append(" · ").append(formatRelativeTime(x.getAddTs()))
              .append("</font>\n");
            sb.append("　要点：").append(safe(entry.getValue())).append("\n");
        }
        return sb.toString();
    }

    private static String buildMarketHotInsightSnippet(WebSearchNews row, String title, String group) {
        return buildMarketHotInsightSnippet(row, title, group, Collections.emptyMap());
    }

    private static List<WebSearchNews> prepareMarketHotItems(List<WebSearchNews> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<WebSearchNews> sorted = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (WebSearchNews row : rows) {
            if (!isMarketHotDisplayable(row)) continue;
            String title = firstNonBlank(row.getEventSummary(), row.getTitle(), row.getContent());
            String key = normalizeComparable(removeOcrSuffix(title));
            if (key.isEmpty()) key = productIdentityKey(row);
            if (!seen.add(key)) continue;
            sorted.add(row);
        }
        sorted.sort((a, b) -> {
            int scoreA = marketHotSignalScore(a);
            int scoreB = marketHotSignalScore(b);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            int rankA = marketHotSourceRank(a);
            int rankB = marketHotSourceRank(b);
            if (rankA != rankB) return Integer.compare(rankA, rankB);
            int impA = a == null || a.getEventImportance() == null ? 0 : a.getEventImportance();
            int impB = b == null || b.getEventImportance() == null ? 0 : b.getEventImportance();
            if (impA != impB) return Integer.compare(impB, impA);
            long tsA = a == null || a.getAddTs() == null ? 0L : a.getAddTs();
            long tsB = b == null || b.getAddTs() == null ? 0L : b.getAddTs();
            return Long.compare(tsB, tsA);
        });
        return sorted;
    }

    private static boolean isMarketHotDisplayable(WebSearchNews row) {
        if (row == null) return false;
        int sourceRank = marketHotSourceRank(row);
        if (sourceRank >= 9) return false;
        String url = row.getUrl() == null ? "" : row.getUrl().toLowerCase(Locale.ROOT);
        if (isMarketHotDealerOrLocalPromo(url, row)) return false;
        String title = firstNonBlank(row.getEventSummary(), row.getTitle(), row.getContent());
        String compact = normalizeComparable(removeOcrSuffix(title));
        if (compact.isEmpty()) return false;
        if (isFixedBenchmarkMarketGroup(compact)) return false;
        if (compact.matches(".*(B端运营|营运市场|出租车|网约车|警用|公务用车).*")) return false;
        if (compact.matches(".*(亚马逊限量版|萧邦版|联名版).*") && !compact.matches(".*(订单|大定|销量|交付|破圈|声量).*")) return false;
        if (compact.matches(".*(充电桩|电桩|渗透率|营收|财报|利润|毛利|车企冠军|销量再夺|隐患排查|汽车行业|新能源行业).*")
                && !hasClearMarketHotVehicleSignal(row)) {
            return false;
        }
        return marketHotSignalScore(row) >= 4;
    }

    private static String marketHotTitle(WebSearchNews row) {
        String title = firstNonBlank(row == null ? null : row.getEventSummary(),
                row == null ? null : row.getTitle(),
                row == null ? null : row.getContent());
        return removeOcrSuffix(truncate(title, MAX_SUMMARY + 35));
    }

    private static int marketHotSignalScore(WebSearchNews row) {
        if (row == null) return 0;
        String text = combineNonBlank(row.getEventSummary(), row.getTitle(), row.getContent(),
                row.getModelMentioned(), row.getBrandName());
        String compact = normalizeComparable(text);
        int score = 0;
        if (compact.matches(".*(大定|订单|锁单|小订).*")) score += 6;
        if (compact.matches(".*(销量|交付|热销|爆款|榜首|冠军|破\\d|突破).*")) score += 5;
        if (compact.matches(".*(投诉|召回|事故|自燃|维权|争议|舆情|安全隐患|口碑).*")) score += 6;
        if (compact.matches(".*(价格战|官降|限时权益|购车权益|置换补贴|金融政策|0息|免息).*")) score += 4;
        if (compact.matches(".*(智驾|NOA|激光雷达|800V|固态电池|电池平台|快充).*")) score += 4;
        if (compact.matches(".*(代言|破圈|出圈|用户共创|社群).*")) score += 3;
        if (compact.matches(".*(上市|发布|预售|亮相).*") && score == 0) score -= 2;
        Integer imp = row.getEventImportance();
        if (imp != null && imp >= 8) score += 1;
        return score;
    }

    private static boolean isMarketHotDealerOrLocalPromo(String url, WebSearchNews row) {
        String u = url == null ? "" : url;
        if (u.matches(".*(/cheshi/|/dealer/|/4s/|/price/|/jiangjia/|/youhui/).*")) return true;
        String text = normalizeComparable(combineNonBlank(row == null ? null : row.getEventSummary(),
                row == null ? null : row.getTitle(),
                row == null ? null : row.getContent()));
        return text.matches(".*(让利促销|店内|到店|现车|本店|购车热线|车主价格|优惠0\\.00|暂无优惠|降价促销|少量现车|询底价).*");
    }

    private static boolean hasClearMarketHotVehicleSignal(WebSearchNews row) {
        if (row == null) return false;
        String model = firstNonBlank(row.getModelMentioned(), "");
        if (!model.isEmpty() && !isInvalidMarketHotGroup(model)) return true;
        String text = firstNonBlank(row.getEventSummary(), row.getTitle(), "") + " " + firstNonBlank(row.getTitle(), "");
        return text.matches(".*(小鹏|岚图|极狐|蔚来|五菱|宏光|智己|深蓝|零跑|阿维塔|乐道|腾势|海鸥|海豹|银河|星光|MINIEV|MG\\s*4X|ES9|T1|GX|D9).*")
                || text.matches(".*[A-Z]{1,4}\\s*\\d{1,3}(?:\\s*(?:Plus|Pro|Max|Ultra|EV|PHEV|REEV))?.*");
    }

    private static int marketHotSourceRank(WebSearchNews row) {
        String url = row == null || row.getUrl() == null ? "" : row.getUrl().toLowerCase(Locale.ROOT);
        if (url.contains("autohome.com.cn") || url.contains("dongchedi.com")) return 0;
        if (url.contains("yiche.com") || url.contains("pcauto.com.cn") || url.contains("gasgoo.com")
                || url.contains("auto.sina.com.cn") || url.contains("auto.sohu.com")) return 1;
        if (url.contains("k.sina.com.cn")) return 2;
        return 9;
    }

    private static String marketHotSourceName(WebSearchNews row) {
        String url = row == null || row.getUrl() == null ? "" : row.getUrl().toLowerCase(Locale.ROOT);
        if (url.contains("autohome.com.cn")) return "汽车之家";
        if (url.contains("dongchedi.com")) return "懂车帝";
        if (url.contains("yiche.com")) return "易车";
        if (url.contains("pcauto.com.cn")) return "太平洋汽车";
        if (url.contains("gasgoo.com")) return "盖世汽车";
        if (url.contains("auto.sina.com.cn") || url.contains("k.sina.com.cn")) return "新浪汽车";
        if (url.contains("auto.sohu.com")) return "搜狐汽车";
        return sourceLabel(row == null ? null : row.getSourceTool());
    }

    private static String buildMarketHotInsightSnippet(WebSearchNews row, String title, String group,
                                                       Map<Long, CardInsight> cardInsights) {
        if (row == null) return "";
        return buildLlmInsightSnippet(cardInsights, row, title, null);
    }

    private static String cleanMarketHotSourceText(String text, String title) {
        if (text == null || text.isEmpty()) return "";
        String normalizedTitle = normalizeComparable(title);
        LinkedHashSet<String> clauses = new LinkedHashSet<>();
        for (String raw : text.split("[\\n。；;！!？?]")) {
            String clause = cleanDigestPoint(raw);
            if (clause.isEmpty()) continue;
            clause = clause.replaceAll("\\s*[-_—–]\\s*(今日头条|中关村在线|搜狐汽车|搜狐网|新浪汽车|汽车之家|易车|懂车帝|太平洋汽车|盖世汽车)\\s*$", "")
                    .replaceAll("(今日头条|中关村在线|搜狐汽车|搜狐网|新浪汽车|汽车之家|易车|懂车帝|太平洋汽车|盖世汽车)$", "")
                    .replaceAll("^[\\u4e00-\\u9fa5A-Za-z0-9]{1,12}[，,：:]", "")
                    .trim();
            if (clause.isEmpty()) continue;
            String comparable = normalizeComparable(clause);
            if (!normalizedTitle.isEmpty() && (normalizedTitle.contains(comparable) || isNearDuplicateText(comparable, normalizedTitle))) {
                continue;
            }
            clauses.add(clause);
        }
        return String.join("。", clauses);
    }

    private static void addMarketHotPart(LinkedHashSet<String> parts, String point, String title, int maxParts) {
        if (parts.size() >= maxParts) return;
        String cleaned = cleanDigestPoint(point);
        if (cleaned.isEmpty()) return;
        if (isDuplicateSnippet(cleaned, title, null)) return;
        for (String existing : parts) {
            if (isDuplicateSnippet(cleaned, existing, null)) return;
        }
        parts.add(cleaned);
    }

    private static String firstRegex(String text, String regex) {
        if (text == null || text.isEmpty()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        if (!matcher.find()) return "";
        return cleanDigestPoint(matcher.group());
    }

    private static List<String> extractMarketHotFeaturePoints(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> points = new LinkedHashSet<>();
        String normalized = normalizeOcrForDisplay(text);
        String[] clauses = normalized.split("[\\n。；;！!？?，,、]");
        for (String raw : clauses) {
            String clause = cleanDigestPoint(raw);
            if (clause.length() < 4 || clause.length() > 40) continue;
            boolean value = clause.matches(".*(智驾|辅助驾驶|座舱|大五座|大六座|空间|续航|电池|充电|800V|四驱|底盘|悬架|冰箱|彩电|沙发|露营|越野|家庭|权益|补贴|置换|订单|大定|销量).*");
            if (!value) continue;
            points.add(clause);
            if (points.size() >= 3) break;
        }
        return new ArrayList<>(points);
    }

    private static String buildMarketHotViewpoint(String text, String model, String orderFact,
                                                  String priceFact, String segment,
                                                  List<String> featurePoints) {
        String v = normalizeContentSnippetText(text);
        String subject = firstNonBlank(model, "该车型");
        boolean hasVolume = !orderFact.isEmpty() || v.matches(".*(订单|大定|锁单|销量|交付).*");
        boolean hasPrice = !priceFact.isEmpty() || v.matches(".*(售价|价格|权益后售价|降价|补贴).*");
        boolean hasFamily = v.matches(".*(家庭|大五座|大六座|六座|亲子|空间|座舱).*");
        boolean hasTech = v.matches(".*(智驾|辅助驾驶|智能|座舱|800V|电池|续航|充电).*");
        boolean hasOffroad = v.matches(".*(越野|硬派|方盒子|四驱|底盘|露营|户外).*");

        List<String> signals = new ArrayList<>();
        if (hasVolume) signals.add("需求验证");
        if (hasPrice) signals.add("价格锚点");
        if (hasFamily) signals.add("家庭场景");
        if (hasTech) signals.add("智能配置");
        if (hasOffroad) signals.add("户外/越野场景");

        if (signals.isEmpty()) return "";
        if (hasVolume && hasFamily) {
            return subject + "的热度核心不是单点上市信息，而是用" + firstNonBlank(segment, "家庭SUV定位")
                    + "带动规模化线索，说明大空间家庭用户仍是高声量入口";
        }
        if (hasPrice && (hasFamily || hasTech || hasOffroad)) {
            return subject + "在" + String.join("、", signals) + "上形成组合卖点，会放大同价位SUV的配置和权益对比压力";
        }
        if (hasVolume) {
            return subject + "已形成短期声量和订单验证，后续值得跟踪到店转化与竞品跟进节奏";
        }
        return subject + "的关注点集中在" + String.join("、", signals) + "，可作为市场投放选题和终端话术参考";
    }

    private static LinkedHashMap<String, List<WebSearchNews>> groupMarketHotRows(List<WebSearchNews> rows) {
        LinkedHashMap<String, List<WebSearchNews>> grouped = new LinkedHashMap<>();
        for (WebSearchNews row : rows) {
            String group = marketHotGroupName(row);
            if (isInvalidMarketHotGroup(group)) continue;
            if (isFixedBenchmarkMarketGroup(group)) continue;
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(row);
        }
        List<Map.Entry<String, List<WebSearchNews>>> entries = new ArrayList<>(grouped.entrySet());
        entries.sort((a, b) -> {
            int scoreA = marketHotScore(a.getValue());
            int scoreB = marketHotScore(b.getValue());
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            return Integer.compare(b.getValue().size(), a.getValue().size());
        });
        LinkedHashMap<String, List<WebSearchNews>> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, List<WebSearchNews>> e : entries) ordered.put(e.getKey(), e.getValue());
        return ordered;
    }

    private static int marketHotScore(List<WebSearchNews> rows) {
        int max = 0;
        int count = rows == null ? 0 : rows.size();
        if (rows != null) {
            for (WebSearchNews row : rows) {
                Integer imp = row == null ? null : row.getEventImportance();
                if (imp != null && imp > max) max = imp;
            }
        }
        return max * 10 + Math.min(count, 9);
    }

    private static List<WebSearchNews> sortMarketHotItems(List<WebSearchNews> rows) {
        List<WebSearchNews> sorted = new ArrayList<>(rows == null ? Collections.emptyList() : rows);
        sorted.sort((a, b) -> {
            int impA = a == null || a.getEventImportance() == null ? 0 : a.getEventImportance();
            int impB = b == null || b.getEventImportance() == null ? 0 : b.getEventImportance();
            if (impA != impB) return Integer.compare(impB, impA);
            long tsA = a == null || a.getAddTs() == null ? 0L : a.getAddTs();
            long tsB = b == null || b.getAddTs() == null ? 0L : b.getAddTs();
            return Long.compare(tsB, tsA);
        });
        return sorted;
    }

    private static String marketHotGroupName(WebSearchNews row) {
        if (row == null) return "其他热点";
        String text = combineNonBlank(row.getModelMentioned(), row.getEventSummary(), row.getTitle(),
                row.getContent(), row.getSearchQuery(), row.getBrandName());
        String compact = text.replaceAll("\\s+", "");
        String model = firstNonBlank(row.getModelMentioned(), row.getBrandName());
        model = model == null ? "" : model.split("[,，、/|]")[0].trim();
        if (!model.isEmpty()
                && !"行业".equals(model)
                && !"市场热度".equals(model)
                && !isInvalidMarketHotGroup(model)) return model;
        String dynamicModel = extractMarketHotModelName(compact);
        if (!dynamicModel.isEmpty()) return dynamicModel;
        return "行业热门车型";
    }

    private static String extractMarketHotModelName(String text) {
        if (text == null || text.isEmpty()) return "";
        List<String> stopWords = Arrays.asList(
                "SUV", "MPV", "EV", "PHEV", "REEV", "OTA", "NOA", "GT7",
                "新能源", "热门车型", "汽车之家", "懂车帝", "太平洋汽车", "盖世汽车");
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?:[\\u4e00-\\u9fa5]{1,6})?(?:[A-Z][A-Z0-9]{1,5}|[A-Z]{1,3}\\d{1,3}|\\d{2,4})(?:Plus|Pro|Max|Ultra|EV|PHEV|REEV)?",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (candidate == null) continue;
            candidate = candidate.replaceAll("^(今日|新款|全新|车型|汽车|新能源)", "").trim();
            if (candidate.length() < 2 || candidate.length() > 18) continue;
            if (isInvalidMarketHotGroup(candidate)) continue;
            boolean stop = false;
            for (String word : stopWords) {
                if (candidate.equalsIgnoreCase(word) || candidate.contains(word)) {
                    stop = true;
                    break;
                }
            }
            if (!stop && candidate.matches(".*[A-Za-z0-9].*")) return candidate;
        }
        return "";
    }

    private static boolean isInvalidMarketHotGroup(String group) {
        if (group == null) return true;
        String v = group.replaceAll("\\s+", "").trim();
        if (v.isEmpty()) return true;
        if ("行业热门车型".equals(v) || "行业".equals(v) || "市场热度".equals(v)) return true;
        if (v.matches(".*(总量|销量|交付|订单|大定|小订|锁单|上市|售价|价格|权益|冠军|亚军|突破|首破|累计|同比|环比|增长|下滑|渗透率|占比|份额|电桩|充电|车企|车市|发布会|车展|预售|直播|用户|市场|营收|收入|利润|毛利|财报|季度|一季度|二季度|三季度|四季度).*")) {
            return true;
        }
        return v.matches("\\d+.*") || v.matches(".*\\d{4,}.*");
    }

    private static boolean isFixedBenchmarkMarketGroup(String group) {
        if (group == null) return false;
        return group.matches(".*(猛士|仰望|坦克|方程豹|问界|AITO|路虎|卫士).*");
    }

    private static String formatNum(Integer n) {
        if (n == null) return "—";
        return String.format("%,d", n);
    }

    private static String sourceLabel(String tool) {
        if (tool == null || tool.isEmpty()) return "未知";
        return SOURCE_TOOL_LABEL.getOrDefault(tool, tool);
    }

    private static String formatPublishTime(Long addTs) {
        if (addTs == null || addTs <= 0) return "时间未知";
        return Instant.ofEpochMilli(addTs).atZone(CN_ZONE).format(PUBLISH_TIME_FMT);
    }

    /** v8+ 相对时间：刚刚 / Nh 前 / N 天前 / M 月 D 日。专业卡片体验首选。 */
    private static String formatRelativeTime(Long addTs) {
        if (addTs == null || addTs <= 0) return "时间未知";
        long diffMs = System.currentTimeMillis() - addTs;
        if (diffMs < 0) return "刚刚";
        long minutes = diffMs / 60_000;
        long hours = diffMs / 3_600_000;
        long days = diffMs / 86_400_000;
        if (minutes < 5) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";
        if (hours < 24) return hours + "h 前";
        if (days < 7) return days + " 天前";
        return Instant.ofEpochMilli(addTs).atZone(CN_ZONE).format(SHORT_DATE_FMT);
    }

    /** v8+ KPI 横排：手机上主动排成 2 行，避免飞书窄屏把单行挤成不规则换行。 */
    private static Map<String, Object> kpiRow(int launchCnt, int priceCnt, int campaignCnt, int salesCnt) {
        String content = String.format(
                "🚀 产品 **<font color='blue'>%d</font>**　｜　"
                        + "💰 价格 **<font color='%s'>%d</font>**\n"
                        + "📣 营销 **%d**　｜　"
                        + "📈 销量 **<font color='green'>%d</font>**",
                launchCnt, COLOR_BRAND, priceCnt, campaignCnt, salesCnt);
        return divNode("lark_md", content);
    }

    /** v8+ collapsible_panel（默认收起的次要内容）。 */
    private static Map<String, Object> collapsiblePanel(String headerTitle, String bodyMd) {
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("tag", "collapsible_panel");
        panel.put("expanded", false);
        Map<String, Object> headerObj = new LinkedHashMap<>();
        headerObj.put("title", textNode("plain_text", headerTitle));
        headerObj.put("background_color", "grey-100");
        headerObj.put("vertical_align", "center");
        headerObj.put("padding", "8px 8px 8px 8px");
        panel.put("header", headerObj);
        List<Object> els = new ArrayList<>();
        els.add(divNode("lark_md", bodyMd));
        panel.put("elements", els);
        return panel;
    }

    /** 卡片用：本品 vs 竞品名列表，1 行简介。 */
    private String buildBenchmarkSummary(BenchmarkMatrix m) {
        if (m.modelNames == null || m.modelNames.size() <= 1) return "";
        StringBuilder sb = new StringBuilder("<font color='grey'>对手：");
        for (int i = 1; i < m.modelNames.size(); i++) {
            if (i > 1) sb.append(" / ");
            sb.append(m.modelNames.get(i));
        }
        sb.append("</font>");
        return sb.toString();
    }

    // ===== 单张对标矩阵 markdown（保留备用，目前已不在卡片中渲染）=====
    private String renderSingleMatrix(BenchmarkMatrix m) {
        StringBuilder sb = new StringBuilder();
        // 标题：本品 vs 竞品 N 款
        String self = m.selfModel == null ? "对标" : m.selfModel;
        sb.append("\n**🔸 ").append(self).append(" vs ")
          .append(Math.max(0, m.modelNames.size() - 1)).append(" 款竞品**\n");
        if (m.paramRows.isEmpty()) {
            sb.append("<font color='grey'>暂无参数 — 去「车系配置」点对应车型「立刻抓取」</font>\n");
            return sb.toString();
        }
        sb.append("\n| 参数 |");
        for (String name : m.modelNames) {
            sb.append(" ").append(truncate(name, 8)).append(" |");
        }
        sb.append("\n|---|");
        for (int i = 0; i < m.modelNames.size(); i++) sb.append("---|");
        sb.append("\n");

        String lastCat = "";
        for (ParamRow row : m.paramRows) {
            String cat = safe(row.category);
            sb.append("| ");
            if (!cat.equals(lastCat)) {
                sb.append("**").append(truncate(cat, 6)).append("** ");
                lastCat = cat;
            }
            sb.append(truncate(safe(row.name), 14)).append(" |");
            for (String model : m.modelNames) {
                String v = row.values.getOrDefault(model, "-");
                sb.append(" ").append(truncate(v, 16)).append(" |");
            }
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) {
                sb.append("\n<font color='grey'>… 表格过长已截断，完整数据见「技术参数对标」页</font>\n");
                break;
            }
        }
        return sb.toString();
    }

    // ===== 销售话术（v2 保留）=====
    private List<Object> renderTalkingPointsByPersona(List<CardTextSections.SuggestedTalkingPoint> tps) {
        List<Object> out = new ArrayList<>();
        if (tps == null || tps.isEmpty()) return out;

        LinkedHashMap<String, List<CardTextSections.SuggestedTalkingPoint>> grouped = tps.stream()
                .filter(tp -> tp.getPersona() != null && !tp.getPersona().isEmpty())
                .collect(Collectors.groupingBy(
                        CardTextSections.SuggestedTalkingPoint::getPersona,
                        LinkedHashMap::new,
                        Collectors.toList()));
        if (grouped.isEmpty()) {
            grouped.put("ALL", new ArrayList<>(tps));
        }

        for (Map.Entry<String, List<CardTextSections.SuggestedTalkingPoint>> e : grouped.entrySet()) {
            String code = e.getKey();
            String name = e.getValue().get(0).getPersonaName();
            String title = "ALL".equals(code)
                    ? "🎯 通用话术"
                    : "🎯 " + safe(name) + " (" + code + ")";
            out.add(personaPanel(title, e.getValue()));
        }
        return out;
    }

    private Map<String, Object> personaPanel(String title, List<CardTextSections.SuggestedTalkingPoint> items) {
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("tag", "collapsible_panel");
        panel.put("expanded", false);

        Map<String, Object> headerObj = new LinkedHashMap<>();
        headerObj.put("title", textNode("plain_text", title));
        headerObj.put("background_color", "grey-100");
        headerObj.put("vertical_align", "center");
        headerObj.put("padding", "8px 8px 8px 8px");
        panel.put("header", headerObj);

        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (CardTextSections.SuggestedTalkingPoint tp : items) {
            sb.append("**").append(i++).append(". ")
              .append(truncate(safe(tp.getScenario()), MAX_SUMMARY + 30))
              .append("**\n");
            if (tp.getTalkingPoint() != null && !tp.getTalkingPoint().isEmpty()) {
                sb.append(truncate(tp.getTalkingPoint(), MAX_SECTION_BYTES / Math.max(items.size(), 1)))
                  .append("\n");
            }
            if (tp.getCompetitorModel() != null && !tp.getCompetitorModel().isEmpty()) {
                sb.append("<font color='grey'>vs ").append(safe(tp.getCompetitorModel())).append("</font>\n");
            }
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) break;
        }
        List<Object> els = new ArrayList<>();
        els.add(divNode("lark_md", sb.toString()));
        panel.put("elements", els);
        return panel;
    }
}
