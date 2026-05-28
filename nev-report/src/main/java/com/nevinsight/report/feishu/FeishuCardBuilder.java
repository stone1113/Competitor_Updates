package com.nevinsight.report.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.MetricsCalculator;
import com.nevinsight.model.dto.response.CardTextSections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

import static com.nevinsight.report.feishu.FeishuCardUtils.*;

/**
 * 飞书 interactive 卡片构建器（紧凑版）。
 * 设计原则：
 *  - 不再"小标题 + 内容"分两个 div（之前每章 3 个元素 = 章节标题 + hr + 内容；现在每章 1 个 lark_md 元素）
 *  - 顶部 KPI 用 column_set 三列横排
 *  - 章节之间不加 hr，靠空行 + 粗体 emoji 标题分隔
 *  - 控制每条内容长度（标题 ≤ 50字、摘要 ≤ 70字）
 *  - 链接走 inline `[标题](url)` 格式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuCardBuilder {

    private final ObjectMapper objectMapper;

    /** 趋势数据点：日期 + 总声量 + 健康度 */
    public static class TrendPoint {
        public final String dateLabel;     // "05-04"
        public final int totalMentions;
        public final double avgScore;
        public TrendPoint(String dateLabel, int totalMentions, double avgScore) {
            this.dateLabel = dateLabel;
            this.totalMentions = totalMentions;
            this.avgScore = avgScore;
        }
    }

    public String build(String brandName, LocalDate date,
                        MetricsCalculator.DailyKPI kpi,
                        CardTextSections sections,
                        List<TrendPoint> trend,
                        String detailUrl) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("msg_type", "interactive");

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("schema", "2.0");
            card.put("config", Collections.singletonMap("width_mode", "fill"));

            // ===== Header =====
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("template", templateColor(kpi.getStatusLevel()));
            header.put("title", textNode("plain_text",
                    String.format("%s · %s 舆情日报", brandName, date)));
            card.put("header", header);

            List<Object> elements = new ArrayList<>();

            // ===== 顶部速览（LLM 生成） =====
            String briefing = sections == null ? null : sections.getDailyBriefing();
            if (briefing != null && !briefing.trim().isEmpty()) {
                elements.add(divNode("lark_md", "<font color='grey'>**📋 今日速览**</font>\n" + briefing.trim()));
            }

            // ===== 顶部 KPI 三列 =====
            elements.add(kpiColumns(kpi));

            // ===== 情感分布单行 =====
            String dist = String.format(
                    "<font color='%s'>%s</font> · 总 %d · 正 %d · 中 %d · 负 %d · 健康度 **%.2f**",
                    statusColor(kpi.getStatusLevel()),
                    statusLabel(kpi.getStatusLevel()),
                    kpi.getTotalMentions(),
                    kpi.getPositiveCount() + kpi.getVeryPositiveCount(),
                    kpi.getNeutralCount(),
                    kpi.getNegativeCount() + kpi.getVeryNegativeCount(),
                    kpi.getAvgSentimentScore());
            elements.add(divNode("lark_md", dist));

            // ===== 危机预警 =====
            String alerts = renderCrisisAlerts(sections);
            if (!alerts.isEmpty()) {
                elements.add(divNode("lark_md", alerts));
            }

            // ===== 趋势图 =====
            Map<String, Object> chartElement = buildTrendChart(trend);
            if (chartElement != null) {
                elements.add(hr());
                elements.add(divNode("lark_md", "**📈 7 日声量趋势**"));
                elements.add(chartElement);
            }

            // ===== 议题热度 =====
            appendSection(elements, renderTopics(sections));

            // ===== 各章节 =====
            appendSection(elements, renderCoreNews(sections));
            appendSection(elements, renderHotDiscussions(sections));
            appendSection(elements, renderIndustry(sections));
            appendSection(elements, renderBenchmark(sections));

            // ===== 按钮（v2：直接放 elements，去掉 action 包装） =====
            if (detailUrl != null && !detailUrl.isEmpty()) {
                elements.add(hr());
                elements.add(actionButton("📊 查看完整日报", detailUrl));
            }

            // ===== 底部注脚（v2：note 已移除，改用 markdown） =====
            elements.add(noteFooter());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("direction", "vertical");
            body.put("elements", elements);
            card.put("body", body);
            root.put("card", card);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("[FeishuCard] 构建失败: {}", e.getMessage(), e);
            return "{\"msg_type\":\"text\",\"content\":{\"text\":\"日报生成失败: " + e.getMessage() + "\"}}";
        }
    }

    /** 兼容旧调用 */
    public String build(String brandName, LocalDate date,
                        MetricsCalculator.DailyKPI kpi,
                        CardTextSections sections) {
        return build(brandName, date, kpi, sections, null, null);
    }

    /** KPI 三列布局：总声量 / 正面率 / 负面率 */
    private Map<String, Object> kpiColumns(MetricsCalculator.DailyKPI kpi) {
        Map<String, Object> set = new LinkedHashMap<>();
        set.put("tag", "column_set");
        set.put("flex_mode", "none");
        set.put("background_style", "default");
        List<Object> cols = new ArrayList<>();
        cols.add(kpiColumn("总声量", String.valueOf(kpi.getTotalMentions()), "default", null));
        cols.add(kpiColumn("正面", String.format("%.1f%%", kpi.getPositiveRatio() * 100), "default", "green"));
        cols.add(kpiColumn("负面", String.format("%.1f%%", kpi.getNegativeRatio() * 100), "default", "red"));
        set.put("columns", cols);
        return set;
    }

    private Map<String, Object> kpiColumn(String label, String value, String bgStyle, String valueColor) {
        Map<String, Object> col = new LinkedHashMap<>();
        col.put("tag", "column");
        col.put("width", "weighted");
        col.put("weight", 1);
        col.put("vertical_align", "top");

        Map<String, Object> container = new LinkedHashMap<>();
        container.put("tag", "interactive_container");
        container.put("background_style", bgStyle);
        container.put("corner_radius", "8px");
        container.put("padding", "10px 12px 10px 12px");

        List<Object> containerEls = new ArrayList<>();
        Map<String, Object> labelEl = new LinkedHashMap<>();
        labelEl.put("tag", "markdown");
        labelEl.put("content", "<font color='grey'>" + label + "</font>");
        containerEls.add(labelEl);

        Map<String, Object> valueEl = new LinkedHashMap<>();
        valueEl.put("tag", "markdown");
        String valueContent = (valueColor == null || valueColor.isEmpty())
                ? "**" + value + "**"
                : "<font color='" + valueColor + "'>**" + value + "**</font>";
        valueEl.put("content", valueContent);
        containerEls.add(valueEl);

        container.put("elements", containerEls);
        col.put("elements", Collections.singletonList(container));
        return col;
    }

    private String renderCoreNews(CardTextSections sections) {
        List<CardTextSections.CoreNewsItem> news = sections == null ? null : sections.getCoreNews();
        if (news == null || news.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("**📰 核心资讯**\n");
        int i = 1;
        for (CardTextSections.CoreNewsItem n : news) {
            sb.append(i++).append(". ");
            sb.append(sentimentBadge(n.getSentiment())).append(" ");
            String title = truncate(n.getTitle(), MAX_TITLE);
            if (n.getSourceUrl() != null && !n.getSourceUrl().isEmpty()) {
                sb.append("[").append(title).append("](").append(n.getSourceUrl()).append(")");
            } else {
                sb.append(title);
            }
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) break;
        }
        return sb.toString();
    }

    private String renderCrisisAlerts(CardTextSections sections) {
        List<String> alerts = sections == null ? null : sections.getCrisisAlerts();
        if (alerts == null || alerts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String a : alerts) {
            if (a == null || a.isEmpty()) continue;
            sb.append("<font color='red'>⚠️</font> ").append(a).append("\n");
        }
        return sb.toString();
    }

    private String renderTopics(CardTextSections sections) {
        List<CardTextSections.TopicCluster> topics = sections == null ? null : sections.getTopics();
        if (topics == null || topics.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("**🏷 议题热度**\n");
        int i = 1;
        for (CardTextSections.TopicCluster t : topics) {
            sb.append(i++).append(". ");
            sb.append(sentimentBadge(t.getSentimentTag())).append(" ");
            sb.append("**").append(safe(t.getName())).append("**");
            if (t.getCount() != null && t.getCount() > 0) {
                sb.append(" · <font color='grey'>").append(t.getCount()).append(" 条</font>");
            }
            String exTitle = truncate(t.getExampleTitle(), MAX_TITLE);
            String exUrl = t.getExampleUrl();
            if (exTitle != null && !exTitle.isEmpty()) {
                sb.append(" → ");
                if (exUrl != null && !exUrl.isEmpty()) {
                    sb.append("[").append(exTitle).append("](").append(exUrl).append(")");
                } else {
                    sb.append(exTitle);
                }
            }
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) break;
        }
        return sb.toString();
    }

    private String renderHotDiscussions(CardTextSections sections) {
        List<CardTextSections.HotDiscussion> hots = sections == null ? null : sections.getHotDiscussions();
        if (hots == null || hots.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("**🔥 热议 TOP").append(hots.size()).append("**\n");
        int i = 1;
        for (CardTextSections.HotDiscussion h : hots) {
            sb.append(i++).append(". ");
            sb.append(sentimentBadge(h.getSentimentTag())).append(" ");
            sb.append("`").append(safe(h.getPlatform())).append("`");
            String title = truncate(stripHashtags(h.getTitle()), MAX_TITLE);
            String url = h.getSourceUrl();
            if (url != null && !url.isEmpty()) {
                sb.append(" [").append(title).append("](").append(url).append(")");
            } else {
                sb.append(" ").append(title);
            }
            String engagement = compactEngagement(h.getStats());
            if (!engagement.isEmpty()) sb.append(" · <font color='grey'>").append(engagement).append("</font>");
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) break;
        }
        return sb.toString();
    }

    private String renderIndustry(CardTextSections sections) {
        List<CardTextSections.IndustryHotspot> industry = sections == null ? null : sections.getIndustryHotspots();
        if (industry == null || industry.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("**🌐 行业热点**\n");
        int i = 1;
        for (CardTextSections.IndustryHotspot ih : industry) {
            sb.append(i++).append(". ");
            String title = truncate(ih.getContent(), MAX_TITLE);
            if (ih.getSourceUrl() != null && !ih.getSourceUrl().isEmpty()) {
                sb.append("[").append(title).append("](").append(ih.getSourceUrl()).append(")");
            } else {
                sb.append(title);
            }
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) break;
        }
        return sb.toString();
    }

    private String renderBenchmark(CardTextSections sections) {
        List<CardTextSections.CompetitorBenchmark> cbs = sections == null ? null : sections.getCompetitorBenchmark();
        if (cbs == null || cbs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("**⚔️ 竞品对比**\n");
        for (CardTextSections.CompetitorBenchmark cb : cbs) {
            sb.append("- **").append(safe(cb.getTopic())).append("** vs ")
              .append(safe(cb.getCompetitorModel()));
            if (cb.getMengshiAdvantage() != null && !cb.getMengshiAdvantage().isEmpty()) {
                sb.append(" → ").append(truncate(cb.getMengshiAdvantage(), MAX_SUMMARY));
            }
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) break;
        }
        return sb.toString();
    }

    private String renderTalkingPoints(CardTextSections sections) {
        List<CardTextSections.SuggestedTalkingPoint> tps = sections == null ? null : sections.getSuggestedTalkingPoints();
        if (tps == null || tps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("**💡 销售话术**\n");
        int i = 1;
        for (CardTextSections.SuggestedTalkingPoint tp : tps) {
            sb.append(i++).append(". ").append(truncate(tp.getScenario(), MAX_SUMMARY + 30));
            if (tp.getTalkingPoint() != null && !tp.getTalkingPoint().isEmpty()) {
                sb.append(" → ").append(truncate(tp.getTalkingPoint(), MAX_SUMMARY));
            }
            sb.append("\n");
            if (sb.length() > MAX_SECTION_BYTES) break;
        }
        return sb.toString();
    }

    private Map<String, Object> buildTrendChart(List<TrendPoint> trend) {
        if (trend == null || trend.isEmpty()) return null;

        // 确保升序（旧→新，左→右）
        List<TrendPoint> ordered = new ArrayList<>(trend);
        ordered.sort(Comparator.comparing(p -> p.dateLabel));

        List<Map<String, Object>> mentionValues = new ArrayList<>();
        List<Map<String, Object>> healthValues = new ArrayList<>();
        for (TrendPoint p : ordered) {
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("date", p.dateLabel);
            mv.put("mentions", p.totalMentions);
            mentionValues.add(mv);

            Map<String, Object> hv = new LinkedHashMap<>();
            hv.put("date", p.dateLabel);
            hv.put("health", p.avgScore);
            healthValues.add(hv);
        }

        // series: 面积图（声量）
        Map<String, Object> areaStyle = new LinkedHashMap<>();
        areaStyle.put("style", Collections.singletonMap("fillOpacity", 0.3));
        Map<String, Object> areaSeries = new LinkedHashMap<>();
        areaSeries.put("type", "area");
        areaSeries.put("dataIndex", 0);
        areaSeries.put("xField", "date");
        areaSeries.put("yField", "mentions");
        areaSeries.put("yAxisIndex", 0);
        areaSeries.put("area", areaStyle);

        // series: 折线（健康度）
        Map<String, Object> lineSeries = new LinkedHashMap<>();
        lineSeries.put("type", "line");
        lineSeries.put("dataIndex", 1);
        lineSeries.put("xField", "date");
        lineSeries.put("yField", "health");
        lineSeries.put("yAxisIndex", 1);

        // data
        Map<String, Object> mentionsData = new LinkedHashMap<>();
        mentionsData.put("id", "mentions");
        mentionsData.put("values", mentionValues);
        Map<String, Object> healthData = new LinkedHashMap<>();
        healthData.put("id", "health");
        healthData.put("values", healthValues);

        // axes
        Map<String, Object> bottomAxis = new LinkedHashMap<>();
        bottomAxis.put("orient", "bottom");
        bottomAxis.put("label", Collections.singletonMap("visible", true));
        Map<String, Object> leftAxis = new LinkedHashMap<>();
        leftAxis.put("orient", "left");
        Map<String, Object> rightAxis = new LinkedHashMap<>();
        rightAxis.put("orient", "right");
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("min", 1);
        range.put("max", 5);
        rightAxis.put("range", range);

        // chart_spec
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", "common");
        spec.put("series", Arrays.asList(areaSeries, lineSeries));
        spec.put("data", Arrays.asList(mentionsData, healthData));
        spec.put("axes", Arrays.asList(bottomAxis, leftAxis, rightAxis));

        // chart element
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("tag", "chart");
        chart.put("aspect_ratio", "2:1");
        chart.put("color_theme", "brand");
        chart.put("chart_spec", spec);
        return chart;
    }

}
