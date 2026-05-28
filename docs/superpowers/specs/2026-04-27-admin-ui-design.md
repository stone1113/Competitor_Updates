# NEV-Insight 管理后台前端设计规格

## 概述

为 NEV-Insight 舆情监控与销售赋能系统构建 Web 管理后台，对接已有的 23 个 REST API 端点。

## 技术栈

- Vue 3.4+ (Composition API + `<script setup>`)
- Vite 5 (构建工具)
- Element Plus 2.7+ (UI 组件库)
- ECharts 5 + vue-echarts (数据可视化)
- Pinia (状态管理)
- Vue Router 4 (路由)
- Axios (HTTP 客户端)
- SCSS (样式)

## 视觉风格

浅色专业风格，适合企业管理层和销售顾问使用。

- 主色：#409EFF (Element Plus 默认蓝)
- 背景：#f5f7fa
- 卡片：#ffffff，圆角 8px，轻阴影
- 成功/正面：#67c23a
- 警告：#e6a23c
- 危险/负面：#f56c6c
- 中性：#909399

## 项目结构

```
nev-admin-ui/
├── public/
├── src/
│   ├── api/                    # API 层（按模块拆分）
│   │   ├── briefing.js         # 日报概览/趋势
│   │   ├── dailyReport.js      # KPI/新闻/热帖/竞品/风险
│   │   ├── competitor.js       # 竞品仪表盘
│   │   ├── dataSource.js       # 数据源配置 CRUD
│   │   ├── cleaningRule.js     # 清洗规则 CRUD
│   │   ├── knowledge.js        # 知识库管理
│   │   ├── talkingPoint.js     # 话术版本管理
│   │   ├── prompt.js           # Prompt 模板管理
│   │   └── pipeline.js         # 日报生成控制
│   ├── views/                  # 页面组件
│   │   ├── daily-report/       # 舆情日报
│   │   │   └── DailyReportView.vue
│   │   ├── competitor/         # 竞品仪表盘
│   │   │   └── CompetitorDashboardView.vue
│   │   ├── collector/          # 采集管理
│   │   │   ├── DataSourceView.vue
│   │   │   └── CleaningRuleView.vue
│   │   ├── knowledge/          # 知识库
│   │   │   └── KnowledgeView.vue
│   │   ├── talking-point/      # 话术管理
│   │   │   └── TalkingPointView.vue
│   │   ├── prompt/             # Prompt 管理
│   │   │   └── PromptView.vue
│   │   └── console/            # 日报控制台
│   │       └── ReportConsoleView.vue
│   ├── components/
│   │   ├── layout/
│   │   │   ├── AppLayout.vue       # 主布局
│   │   │   ├── AppSidebar.vue      # 侧边栏
│   │   │   └── AppHeader.vue       # 顶栏
│   │   └── charts/
│   │       ├── SentimentTrendChart.vue
│   │       ├── PlatformPieChart.vue
│   │       └── CompetitorRadarChart.vue
│   ├── composables/
│   │   └── useApi.js           # Axios 实例 + 拦截器
│   ├── router/
│   │   └── index.js
│   ├── stores/
│   │   └── app.js              # 侧边栏状态
│   ├── styles/
│   │   ├── global.scss
│   │   └── variables.scss
│   ├── App.vue
│   └── main.js
├── index.html
├── vite.config.js
└── package.json
```

## 页面设计

### 1. 舆情��报 `/daily-report` (核心页面)

**布局**：KPI 卡片行 → 图表行 → Tab 内容分区

**KPI 卡片行** (6 个 el-card)：
- 态势等级（绿/黄/红 el-tag 徽章）
- 正面占比（百分比 + 环比箭头）
- 负面占比（百分比 + 环比箭头）
- 情感健康度（1-5 分仪表）
- 总声量（数字 + 环比变化）
- 风险预警数（数字 + 等级色块）

**图表行** (el-row col 16:8)：
- 左：情感趋势折线图（ECharts，7d/30d 切换）
- 右：平台分布饼图（ECharts）

**Tab 内容区** (el-tabs)：
- 核心资讯：5 条新闻卡片，每条含 tag(品牌/竞品/行业)、sentiment(正面/负面/中性)、标题、分析、来源链接
- 热议话题：热帖列表，含平台图标、作者、互动量、情感标签
- 竞品动态：按竞品品牌分组卡片，含 action/sentiment/marketing
- 风险预警：红/黄/绿三级分区显示
- 销售话术：场景化卡片，含猛士车型、对标车型、客户场景、话术内容、技术参考

**数据源**：`GET /api/v1/daily-report/kpi`、`/top-news`、`/top-buzz`、`/buzz`，`GET /api/v1/briefing/overview`、`/trend`

### 2. 竞品仪表盘 `/competitor-dashboard`

- 日期选择器
- ECharts 雷达图（12 维度：续航/智驾/底盘/动力/安全/内饰/空间/价格/口碑/服务/智能化/设计）
- el-table 多品牌对比表格
- 数据源：`GET /api/v1/competitor-dashboard`

### 3. 数据源配置 `/collector/data-source`

- el-table CRUD（source_type、source_name、brand_name、keywords、cron、enabled 开关）
- 新增/编辑 el-dialog 弹窗
- keywords 字段用 el-tag 动态标签输入
- 数据源：`GET/POST/PUT/DELETE /api/v1/data-source`

### 4. 清洗规则 `/collector/cleaning-rule`

- el-table CRUD（rule_type 下拉、rule_name、rule_config JSON 编辑、priority、enabled 开关）
- 数据源：复用 data-source 的 CRUD 模式

### 5. 知识库管理 `/knowledge`

- 文档列表 el-table（doc_name、doc_type、chunk_count、status 进度标签）
- 上传按钮 el-upload（支持 Excel/PDF）
- 状态用 el-tag（PENDING/PROCESSING/DONE/FAILED 四色）
- 数据源��`GET/POST/DELETE /api/v1/knowledge`

### 6. 话术管理 `/talking-point`

- 左侧：话术 ID 列表
- 右侧：版本历史 el-timeline，当前版本高亮
- 回滚按钮 el-popconfirm 确认
- 数据源：`GET /api/v1/talking-point/{id}/versions`、`/current`、`POST .../rollback/{version}`

### 7. Prompt 管理 `/prompt`

- 顶部：Agent 下拉筛选（SENTIMENT_AGENT / COMPETITOR_AGENT / PITCH_AGENT / ASSEMBLY_AGENT）
- 模板列表 el-table
- 编辑弹窗：大文本 el-input textarea（rows=20）编辑 Prompt ��容
- 变量标签显示
- 数据源：`GET/POST/PUT /api/v1/prompt`、`GET /api/v1/prompt/active/{agentName}`

### 8. 日报控制台 `/report-console`

- 日期选择 + 品牌选择
- "生成日报" el-button (type=primary)
- 生成状态（loading/success/error）
- "推送测试群" / "推送生产群" 按钮
- 最近生成记录列表
- 数据源：`POST /api/v1/report-pipeline/generate`

## 后端对接

Vite 开发代理：
```js
// vite.config.js
proxy: {
  '/api': {
    target: 'http://localhost:8090',
    changeOrigin: true
  }
}
```

Docker 部署时由 nginx 反向代理到 Spring Boot 8090 端口。

## 非功能需求

- 响应式：适配 1280px+ 桌面分辨率
- 中文界面（不需要国际化）
- 无需登录认证（内网使用）
