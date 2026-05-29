package com.nevinsight.report.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompetitorCardBuilderTest {

    @Test
    void highlightImportant_keeps_price_unit_and_suffix_together() throws Exception {
        String text = "64.98万元起 | 至高52000元购车权益 | 47.98万元起";

        String highlighted = highlightImportant(text);

        assertThat(highlighted).contains("**64.98万元起**");
        assertThat(highlighted).contains("**至高52000元购车权益**");
        assertThat(highlighted).contains("**47.98万元起**");
        assertThat(highlighted).doesNotContain("**64.98万**元起");
        assertThat(highlighted).doesNotContain("**47.98万**元起");
    }

    @Test
    void highlightImportant_keeps_fuel_consumption_together() throws Exception {
        String text = "WLTC工况下6.5L/100km | 涉水深度800mm";

        String highlighted = highlightImportant(text);

        assertThat(highlighted).contains("**6.5L/100km**");
        assertThat(highlighted).doesNotContain("6.5L/**100km**");
    }

    @Test
    @SuppressWarnings("unchecked")
    void card_details_are_collapsed_with_feedback_buttons_visible() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CompetitorCardBuilder builder = new CompetitorCardBuilder(objectMapper);

        String json = builder.build("猛士汽车", LocalDate.of(2026, 5, 29),
                "", Map.of(), List.of(), List.of(), null);

        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        Map<String, Object> card = (Map<String, Object>) root.get("card");
        Map<String, Object> body = (Map<String, Object>) card.get("body");
        List<Object> elements = (List<Object>) body.get("elements");

        assertThat(indexOfButtonText(elements, "🤖 智能竞品对标")).isEqualTo(-1);

        Map<String, Object> panel = findCollapsedPanel(elements);
        assertThat(panel).isNotNull();
        assertThat(panel.get("expanded")).isEqualTo(false);
        Map<String, Object> header = (Map<String, Object>) panel.get("header");
        Map<String, Object> title = (Map<String, Object>) header.get("title");
        assertThat(title.get("tag")).isEqualTo("markdown");
        assertThat(title.get("content")).isEqualTo("**<font color='blue'>点击展开全部明细 ▼</font>**");

        List<Object> panelElements = (List<Object>) panel.get("elements");
        assertThat(indexOfButtonText(panelElements, "🤖 智能竞品对标")).isEqualTo(-1);
        assertThat(json).doesNotContain("技术对标矩阵", "🤖 智能竞品对标");
        assertThat(findMarkdownContaining(panelElements, "这份日报对你有帮助吗？")).isEqualTo(-1);

        int feedbackTextIndex = findMarkdownContaining(elements, "这份日报对你有帮助吗？");
        assertThat(feedbackTextIndex).isGreaterThanOrEqualTo(0);
        Map<String, Object> feedbackRow = (Map<String, Object>) elements.get(feedbackTextIndex + 1);
        assertThat(feedbackRow.get("tag")).isEqualTo("column_set");
        assertThat(json).contains("\"content\":\"有用\"", "\"content\":\"不准\"", "\"content\":\"太长\"");
        assertThat(json).contains("\"type\":\"callback\"", "\"action\":\"daily_report_feedback\"");
        assertThat(json).contains("\"feedback\":\"useful\"", "\"feedback\":\"inaccurate\"", "\"feedback\":\"too_long\"");
        assertThat(json).contains("\"reportDate\":\"2026-05-29\"");
        assertThat(json).doesNotContain("\"brand\":");
        assertThat(json).doesNotContain("NEV-Insight");
    }

    @Test
    void renderStructuredBriefing_allows_longer_briefing_lines() throws Exception {
        String dynamic = "事件一问界M9上市并公布47.98万元起售价，事件二坦克300新款上市强化价格竞争，事件三小鹏GX大定破两万，事件四猛士M817开启预售传播，事件五仰望U9X发布量产3D打印车身。";
        String risk = "问界M9和坦克300同时强化价格与配置会挤压猛士终端转化，尤其在高端SUV用户比价阶段、门店报价对比阶段、预售权益沟通阶段持续形成压力，风险尾部现在应继续展示。";
        String strategy = "围绕华为乾崑、越野性能和预售权益强化传播，同时准备终端对比话术、价格权益说明和重点竞品分流监控，策略尾部现在应继续展示。";
        String briefing = "动态总结：" + dynamic + "\n风险提示：" + risk + "\n应对策略：" + strategy;

        String rendered = renderStructuredBriefing(briefing);

        assertThat(rendered).contains("事件五仰望U9X发布量产3D打印车身");
        assertThat(rendered).contains("风险尾部现在应继续展示");
        assertThat(rendered).contains("策略尾部现在应继续展示");
    }

    private static String highlightImportant(String text) throws Exception {
        Method method = CompetitorCardBuilder.class.getDeclaredMethod("highlightImportant", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, text);
    }

    private static String renderStructuredBriefing(String text) throws Exception {
        Method method = CompetitorCardBuilder.class.getDeclaredMethod("renderStructuredBriefing", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, text);
    }

    @SuppressWarnings("unchecked")
    private static int indexOfButtonText(List<Object> elements, String text) {
        for (int i = 0; i < elements.size(); i++) {
            Object element = elements.get(i);
            if (!(element instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) element;
            if (!"button".equals(map.get("tag"))) continue;
            Map<String, Object> textNode = (Map<String, Object>) map.get("text");
            if (textNode != null && text.equals(textNode.get("content"))) return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findCollapsedPanel(List<Object> elements) {
        for (Object element : elements) {
            if (!(element instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) element;
            if ("collapsible_panel".equals(map.get("tag"))) return map;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static int findMarkdownContaining(List<Object> elements, String content) {
        for (int i = 0; i < elements.size(); i++) {
            Object element = elements.get(i);
            if (!(element instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) element;
            if (!"div".equals(map.get("tag"))) continue;
            Map<String, Object> text = (Map<String, Object>) map.get("text");
            if (text != null && String.valueOf(text.get("content")).contains(content)) return i;
        }
        return -1;
    }
}
