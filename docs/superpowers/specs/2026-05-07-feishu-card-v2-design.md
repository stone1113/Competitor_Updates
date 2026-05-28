# 飞书卡片升级设计：JSON 2.0 + VChart 趋势图

**日期：** 2026-05-07  
**范围：** `nev-report/src/main/java/com/nevinsight/report/feishu/FeishuCardBuilder.java`（单文件原地改写，Pipeline 调用方零改动）

---

## 目标

1. 将飞书卡片从 JSON 1.0 升级到 JSON 2.0，解锁新组件能力
2. 用 VChart `chart` 组件替换现有的 Unicode 字符画趋势图
3. 用 `interactive_container` 彩色背景卡片替换 KPI 区的字体颜色方案

---

## 一、JSON 2.0 结构迁移

| 字段 | 当前（v1） | 升级后（v2） |
|------|-----------|-------------|
| schema 声明 | 无 | `card` 对象内加 `"schema": "2.0"` |
| 内容挂载点 | `card.elements` | `card.body.elements`（新增 `body` 层） |
| 宽屏配置 | `config.wide_screen_mode: true` | `config.width_mode: "fill"` |
| 底部注脚 | `tag: "note"` | 改为 `tag: "div"` + `lark_md` 灰色小字 |
| 按钮行 | `tag: "action"` 包裹 | 按钮直接放 `elements`，移除 `action` 包装层 |

其余组件（`column_set`、`hr`、`div`/`lark_md`）v1/v2 兼容，不需修改。

**骨架变化：**
```java
// 旧
card.put("config", Map.of("wide_screen_mode", true));
card.put("elements", elements);

// 新
card.put("schema", "2.0");
card.put("config", Map.of("width_mode", "fill"));
Map<String, Object> body = new LinkedHashMap<>();
body.put("direction", "vertical");
body.put("elements", elements);
card.put("body", body);
```

---

## 二、趋势图：VChart chart 组件

### 替换范围
`renderTrendChart(List<TrendPoint> trend)` 从返回 `String` 改为返回 `Map<String, Object>`（与其他 render* 方法保持一致）。

### 数据维度
- `totalMentions`（声量，左 Y 轴，面积图）
- `avgScore`（健康度 1–5，右 Y 轴，折线图）

### VChart JSON 结构
```json
{
  "tag": "chart",
  "aspect_ratio": "2:1",
  "color_theme": "brand",
  "chart_spec": {
    "type": "common",
    "series": [
      {
        "type": "area",
        "dataIndex": 0,
        "xField": "date",
        "yField": "mentions",
        "yAxisIndex": 0,
        "area": { "style": { "fillOpacity": 0.3 } }
      },
      {
        "type": "line",
        "dataIndex": 1,
        "xField": "date",
        "yField": "health",
        "yAxisIndex": 1
      }
    ],
    "data": [
      {
        "id": "mentions",
        "values": [{ "date": "04-28", "mentions": 0 }, { "date": "05-07", "mentions": 44 }]
      },
      {
        "id": "health",
        "values": [{ "date": "04-28", "health": 3.0 }, { "date": "05-07", "health": 3.0 }]
      }
    ],
    "axes": [
      { "orient": "bottom", "label": { "visible": true } },
      { "orient": "left" },
      { "orient": "right", "range": { "min": 1, "max": 5 } }
    ]
  }
}
```

### 数据转换
`List<TrendPoint>` 转换为两组 values 数组，每项结构：`{"date": "MM-dd", "mentions": N, "health": X.X}`，日期按升序（旧→新，左→右）。

### 调用方变化
`build()` 里原来的：
```java
String trendBlock = renderTrendChart(trend);
if (!trendBlock.isEmpty()) {
    elements.add(hr());
    elements.add(divNode("lark_md", trendBlock));
}
```
改为：
```java
Map<String, Object> chartElement = buildTrendChart(trend);
if (chartElement != null) {
    elements.add(hr());
    elements.add(chartElement);
}
```
方法签名：`private Map<String, Object> buildTrendChart(List<TrendPoint> trend)`，返回 `null` 表示无数据。

### 降级
`trend` 为空列表时返回 `null`，调用方跳过 chart 块，行为与现有字符画逻辑一致。

---

## 三、KPI 区：interactive_container 彩色背景

### 替换范围
`kpiColumns()` 和 `kpiColumn()` 方法。三列仍用 `column_set` 横排。

### 颜色映射
| 指标 | `background_style` |
|------|--------------------|
| 总声量 | `"grey"` |
| 正面率 | `"green"` |
| 负面率 | `"red"` |

### 组件结构
```json
{
  "tag": "column_set",
  "columns": [{
    "tag": "column",
    "width": "weighted",
    "weight": 1,
    "elements": [{
      "tag": "interactive_container",
      "background_style": "green",
      "corner_radius": "8px",
      "padding": "12px 12px 12px 12px",
      "elements": [
        { "tag": "markdown", "content": "<font color='grey'>正面</font>" },
        { "tag": "markdown", "content": "**34.1%**" }
      ]
    }]
  }]
}
```

`background_style` 使用飞书 v2 预设色值，不需要自定义 hex。`corner_radius` 和 `padding` 仅在 v2 生效。

---

## 四、不在本次范围内

- 热议列表的 `collapsible_panel` 折叠（可作后续迭代）
- 新闻列表改 `table` 组件（可作后续迭代）
- 引入消息模板 `template_id`（设计与代码解耦，独立需求）

---

## 五、测试验证

改完后通过 `POST /api/v1/report-pipeline/generate?push=true&persist=false` 发一条测试日报，在飞书确认：
1. 趋势图渲染为真实图表（非字符画）
2. KPI 三列有彩色背景
3. 卡片宽屏正常、底部注脚显示、按钮可点击
