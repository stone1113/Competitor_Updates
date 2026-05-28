# 飞书卡片 JSON 2.0 + VChart 趋势图实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `FeishuCardBuilder.java` 从飞书卡片 JSON 1.0 升级到 2.0，用 VChart 面积+折线双轴图替换字符画趋势图，用彩色背景卡片替换 KPI 区字体颜色方案。

**Architecture:** 单文件原地改写 `FeishuCardBuilder.java`，不新增文件，不改 `DailyReportPipeline`（调用方零改动）。先补单元测试使其 FAIL，再改实现使其 PASS，最后 curl 实测推送到飞书验证视觉效果。

**Tech Stack:** Java 11, Spring Boot 2.7.18, Jackson ObjectMapper, 飞书卡片 JSON 2.0 规范, VChart（飞书内置）

---

## 文件清单

| 操作 | 路径 |
|------|------|
| 新建（测试） | `nev-report/src/test/java/com/nevinsight/report/feishu/FeishuCardBuilderTest.java` |
| 修改（实现） | `nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java` |

---

## Task 1：JSON 2.0 骨架迁移 + 破坏性变更修复

> 修复 5 处 v1→v2 破坏性变更：schema 声明、body 层、config、note→markdown、action wrapper 移除。

**Files:**
- Create: `nev-report/src/test/java/com/nevinsight/report/feishu/FeishuCardBuilderTest.java`
- Modify: `nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java`

- [ ] **Step 1：写失败测试**

新建文件 `nev-report/src/test/java/com/nevinsight/report/feishu/FeishuCardBuilderTest.java`，内容如下：

```java
package com.nevinsight.report.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.service.MetricsCalculator;
import com.nevinsight.model.dto.response.CardTextSections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCardBuilderTest {

    private FeishuCardBuilder builder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        builder = new FeishuCardBuilder(objectMapper);
    }

    private MetricsCalculator.DailyKPI sampleKpi() {
        return MetricsCalculator.DailyKPI.builder()
                .totalMentions(44).positiveRatio(0.341).negativeRatio(0.364)
                .avgSentimentScore(3.0).statusLevel("red")
                .veryPositiveCount(1).positiveCount(14).neutralCount(13)
                .negativeCount(15).veryNegativeCount(1)
                .build();
    }

    // ===== Task 1: JSON 2.0 骨架 =====

    @Test
    void card_has_schema_v2() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        Map<?, ?> card = card(json);
        assertThat(card.get("schema")).isEqualTo("2.0");
    }

    @Test
    void card_config_uses_width_mode_fill() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        Map<?, ?> config = (Map<?, ?>) card(json).get("config");
        assertThat(config.get("width_mode")).isEqualTo("fill");
        assertThat(config).doesNotContainKey("wide_screen_mode");
    }

    @Test
    void elements_are_under_body_not_card() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        Map<?, ?> card = card(json);
        assertThat(card).doesNotContainKey("elements");
        Map<?, ?> body = (Map<?, ?>) card.get("body");
        assertThat(body).isNotNull();
        assertThat(body.get("elements")).isInstanceOf(List.class);
    }

    @Test
    void no_note_or_action_tags_in_output() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), "http://example.com");
        assertThat(json).doesNotContain("\"note\"");
        assertThat(json).doesNotContain("\"action\"");
    }

    // ===== Task 2: KPI interactive_container =====

    @Test
    void kpi_columns_use_interactive_container() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        assertThat(json).contains("\"interactive_container\"");
        assertThat(json).contains("\"background_style\"");
    }

    // ===== Task 3: VChart 趋势图 =====

    @Test
    void trend_chart_present_when_data_provided() throws Exception {
        List<FeishuCardBuilder.TrendPoint> trend = List.of(
                new FeishuCardBuilder.TrendPoint("05-04", 50, 3.2),
                new FeishuCardBuilder.TrendPoint("05-07", 44, 3.0)
        );
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, trend, null);
        List<?> elements = bodyElements(json);
        boolean hasChart = elements.stream()
                .filter(e -> e instanceof Map)
                .anyMatch(e -> "chart".equals(((Map<?, ?>) e).get("tag")));
        assertThat(hasChart).isTrue();
    }

    @Test
    void trend_chart_absent_when_empty_trend() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, List.of(), null);
        assertThat(json).doesNotContain("\"chart\"");
    }

    @Test
    void trend_chart_absent_when_null_trend() throws Exception {
        String json = builder.build("猛士", LocalDate.of(2026, 5, 7), sampleKpi(), null, null, null);
        assertThat(json).doesNotContain("\"chart\"");
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    private Map<?, ?> card(String json) throws Exception {
        return (Map<?, ?>) objectMapper.readValue(json, Map.class).get("card");
    }

    @SuppressWarnings("unchecked")
    private List<?> bodyElements(String json) throws Exception {
        Map<?, ?> body = (Map<?, ?>) card(json).get("body");
        return (List<?>) body.get("elements");
    }
}
```

- [ ] **Step 2：运行测试，确认全部 FAIL**

```bash
mvn test -pl nev-report -am -DskipTests=false -Dtest=FeishuCardBuilderTest 2>&1 | tail -20
```

预期：`Tests run: 7, Failures: X, Errors: Y`（有失败/错误），其中 Task 1 的 4 个测试失败。

- [ ] **Step 3：修改 `build()` 方法骨架**

在 `FeishuCardBuilder.java` 中，将 `build()` 方法里这一段：

```java
Map<String, Object> card = new LinkedHashMap<>();
card.put("config", Collections.singletonMap("wide_screen_mode", true));
// ...（header 和 elements 构建）
card.put("elements", elements);
root.put("card", card);
```

替换为：

```java
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

// ...（中间各章节构建保持不变）

Map<String, Object> body = new LinkedHashMap<>();
body.put("direction", "vertical");
body.put("elements", elements);
card.put("body", body);
root.put("card", card);
```

完整的 `build()` 方法开头改后如下（只改骨架，中间内容不动）：

```java
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

        // ===== 趋势图 =====
        Map<String, Object> chartElement = buildTrendChart(trend);
        if (chartElement != null) {
            elements.add(hr());
            elements.add(divNode("lark_md", "**📈 7 日声量趋势**"));
            elements.add(chartElement);
        }

        // ===== 各章节 =====
        appendSection(elements, renderCoreNews(sections));
        appendSection(elements, renderHotDiscussions(sections));
        appendSection(elements, renderIndustry(sections));
        appendSection(elements, renderBenchmark(sections));
        appendSection(elements, renderTalkingPoints(sections));

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
```

- [ ] **Step 4：修改 `noteFooter()` 方法（note → markdown div）**

将现有 `noteFooter()` 方法替换为：

```java
private Map<String, Object> noteFooter() {
    String content = "<font color='grey'><i>NEV-Insight · "
            + new SimpleDateFormat("MM-dd HH:mm").format(new Date()) + "</i></font>";
    return divNode("lark_md", content);
}
```

- [ ] **Step 5：修改 `actionButton()` 方法（移除 action 包装层）**

将现有 `actionButton()` 方法替换为：

```java
private Map<String, Object> actionButton(String text, String url) {
    Map<String, Object> btn = new LinkedHashMap<>();
    btn.put("tag", "button");
    btn.put("type", "primary");
    btn.put("text", textNode("plain_text", text));
    Map<String, Object> mu = new LinkedHashMap<>();
    mu.put("default_url", url);
    mu.put("pc_url", url);
    mu.put("ios_url", url);
    mu.put("android_url", url);
    btn.put("multi_url", mu);
    return btn;
}
```

- [ ] **Step 6：添加占位方法（让编译通过）**

在 `FeishuCardBuilder.java` 中加入一个临时的 `buildTrendChart()` 方法（供 Task 3 替换），让代码先通过编译：

```java
private Map<String, Object> buildTrendChart(List<TrendPoint> trend) {
    return null; // 临时占位，Task 3 替换
}
```

同时删除原有的 `renderTrendChart()` 方法（连同 `blocks` 字符数组变量）。

- [ ] **Step 7：运行 Task 1 的 4 个骨架测试，确认 PASS**

```bash
mvn test -pl nev-report -am -DskipTests=false \
  -Dtest="FeishuCardBuilderTest#card_has_schema_v2+card_config_uses_width_mode_fill+elements_are_under_body_not_card+no_note_or_action_tags_in_output" \
  2>&1 | tail -10
```

预期：`Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 8：Commit**

```bash
cd /Users/anliwu/claude-pro/m-monitor
git add nev-report/src/test/java/com/nevinsight/report/feishu/FeishuCardBuilderTest.java \
        nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java
git commit -m "feat(feishu): migrate card to JSON 2.0 schema, fix breaking changes (note/action/config)"
```

---

## Task 2：KPI 区升级为 interactive_container 彩色背景

**Files:**
- Modify: `nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java:128-162`

- [ ] **Step 1：运行 KPI 测试，确认 FAIL**

```bash
mvn test -pl nev-report -am -DskipTests=false \
  -Dtest="FeishuCardBuilderTest#kpi_columns_use_interactive_container" \
  2>&1 | tail -10
```

预期：`Tests run: 1, Failures: 1`

- [ ] **Step 2：替换 `kpiColumn()` 方法**

将 `FeishuCardBuilder.java` 中的 `kpiColumn()` 方法（第 141-163 行）整体替换为：

```java
private Map<String, Object> kpiColumn(String label, String value, String bgStyle) {
    Map<String, Object> col = new LinkedHashMap<>();
    col.put("tag", "column");
    col.put("width", "weighted");
    col.put("weight", 1);
    col.put("vertical_align", "top");

    Map<String, Object> container = new LinkedHashMap<>();
    container.put("tag", "interactive_container");
    container.put("background_style", bgStyle);
    container.put("corner_radius", "8px");
    container.put("padding", "12px 12px 12px 12px");

    List<Object> containerEls = new ArrayList<>();
    Map<String, Object> labelEl = new LinkedHashMap<>();
    labelEl.put("tag", "markdown");
    labelEl.put("content", "<font color='grey'>" + label + "</font>");
    containerEls.add(labelEl);

    Map<String, Object> valueEl = new LinkedHashMap<>();
    valueEl.put("tag", "markdown");
    valueEl.put("content", "**" + value + "**");
    containerEls.add(valueEl);

    container.put("elements", containerEls);
    col.put("elements", Collections.singletonList(container));
    return col;
}
```

- [ ] **Step 3：更新 `kpiColumns()` 方法中总声量的 bgStyle**

在 `kpiColumns()` 方法中，将：

```java
cols.add(kpiColumn("总声量", String.valueOf(kpi.getTotalMentions()), "default"));
```

改为：

```java
cols.add(kpiColumn("总声量", String.valueOf(kpi.getTotalMentions()), "grey"));
```

正面/负面两列的颜色参数不变（`"green"` 和 `"red"`）。

- [ ] **Step 4：运行 KPI 测试，确认 PASS**

```bash
mvn test -pl nev-report -am -DskipTests=false \
  -Dtest="FeishuCardBuilderTest#kpi_columns_use_interactive_container" \
  2>&1 | tail -10
```

预期：`Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 5：Commit**

```bash
git add nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java
git commit -m "feat(feishu): replace KPI columns with interactive_container colored cards"
```

---

## Task 3：用 VChart chart 组件替换字符画趋势图

**Files:**
- Modify: `nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java`（替换占位 `buildTrendChart()`）

- [ ] **Step 1：运行趋势图测试，确认 FAIL**

```bash
mvn test -pl nev-report -am -DskipTests=false \
  -Dtest="FeishuCardBuilderTest#trend_chart_present_when_data_provided+trend_chart_absent_when_empty_trend+trend_chart_absent_when_null_trend" \
  2>&1 | tail -10
```

预期：`Tests run: 3, Failures: 1, Errors: 0`（`trend_chart_present_when_data_provided` 失败，另外两个因占位返回 null 而通过）

- [ ] **Step 2：将 `buildTrendChart()` 占位方法替换为完整实现**

```java
private Map<String, Object> buildTrendChart(List<TrendPoint> trend) {
    if (trend == null || trend.isEmpty()) return null;

    // 确保升序（旧→新，左→右）
    List<TrendPoint> ordered = new ArrayList<>(trend);

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
```

在文件顶部的 import 确认有 `import java.util.Arrays;`（`java.util.*` 已覆盖）。

- [ ] **Step 3：运行全部趋势图测试，确认 PASS**

```bash
mvn test -pl nev-report -am -DskipTests=false \
  -Dtest="FeishuCardBuilderTest#trend_chart_present_when_data_provided+trend_chart_absent_when_empty_trend+trend_chart_absent_when_null_trend" \
  2>&1 | tail -10
```

预期：`Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 4：运行全套测试，确认全部 7 个通过**

```bash
mvn test -pl nev-report -am -DskipTests=false -Dtest=FeishuCardBuilderTest 2>&1 | tail -10
```

预期：`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5：Commit**

```bash
git add nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java
git commit -m "feat(feishu): replace ASCII trend chart with VChart area+line dual-axis component"
```

---

## Task 4：线上验证（推送到飞书确认视觉效果）

**Files:** 无代码改动

- [ ] **Step 1：构建项目**

```bash
mvn package -DskipTests -pl nev-admin -am 2>&1 | tail -5
```

预期：`BUILD SUCCESS`

- [ ] **Step 2：推送测试日报**

```bash
curl -s -X POST "http://localhost:8090/api/v1/report-pipeline/generate?push=true&persist=false" \
  -H "Content-Type: application/json" --max-time 120 | python3 -m json.tool | grep -E '"success"|"error"'
```

预期输出：`"success": true`

- [ ] **Step 3：飞书确认以下 3 项**

1. 趋势图区域渲染为真实的面积+折线图（非字符 `▁▂▇█`）
2. KPI 三列（总声量/正面/负面）有灰/绿/红彩色背景
3. 底部注脚正常显示时间戳，按钮可点击跳转

如果飞书客户端版本低于 7.20 导致某组件不渲染，回退方案：在 `build()` 方法里保留字符画作为 fallback，用 `try/catch` 包裹 `buildTrendChart()`。
