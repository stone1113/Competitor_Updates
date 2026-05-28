# 变更日志

> 项目所有重要版本变更，按时间倒序记录。
> 业务背景见 `docs/竞品动态-业务方案.md`，架构细节见 `CLAUDE.md`。

---

## v9.7（当前）— 飞书 OAuth + H5 + 移动端适配
日期：2026-05-26

### 新增
- **飞书应用 OAuth 登录**（`FeishuAuthController`）：H5 强制飞书账号登录后才能访问
  - `GET /api/v1/feishu-auth/config` / `/login` / `/callback`
  - `FEISHU_APP_ID` / `FEISHU_APP_SECRET` / `FEISHU_OAUTH_REDIRECT` / `FEISHU_REQUIRE_LOGIN` 配置
- **H5 独立全屏页** `/h5/benchmark`（`BenchmarkH5StandaloneView.vue`）
  - 跳过 m-monitor 侧栏布局，iframe 全屏
  - 浮动用户徽章（右上角，可展开退出）
  - iOS safe-area 适配（刘海屏）
- **RAGFlow 移动端 CSS patch**（`mcp-server/ragflow-mobile-patch.css` + `apply-ragflow-mobile-patch.sh`）
  - 注入 RAGFlow 容器 `/ragflow/web/dist/` 目录
  - 仅 `@media (max-width: 768px)` 生效，桌面不受影响
- **iframe URL 优化**：`visible_avatar=1`（隐藏头像）+ `data_user_id={openId}`（per-user 上下文）

### 修改
- 飞书卡片技术对标矩阵下方简化为**单个「🤖 智能竞品对标」按钮**（不再每车单独出按钮）
- `CompetitorReportController.deepAnalysisUrl()` 加 `userId` 参数透传 openId 到 iframe
- 切到 RAGFlow Agent（`/agent/share?from=agent`），不再用 Chat Assistant

### 修复
- MCP server `httpx.Client(...)` 加 `trust_env=False`，绕开系统代理（Clash/WARP 返 400 "HTTPS port" 错误）
- FastMCP DNS rebinding protection 关闭，允许 `host.docker.internal` 访问

### 数据
- 修正错误 series_id：
  - 猛士M817：7172（实为鲸卡T7EV）→ 8091（清 192 条错数据）
  - 猛士917：5856 → 6907
  - 方程豹豹5：7234 → 7177
  - 坦克700新能源：7100 → 7504
- 同步数据：M817 / 917 / 豹5 / 坦克700新能源 共 2362 条参数

---

## v9.6 — RAGFlow 混合架构（已被 v9.7 简化）
日期：2026-05-26

### 新增
- **`nev-monitor-mcp` Python MCP server**（`mcp-server/`）
  - FastMCP + SSE，跑在 :8092
  - 4 个工具：`query_autohome_spec` / `query_social_voc` / `get_customer_persona` / `create_sales_training_doc`
- **`sales_training_doc` 表**（备用文档承载体）+ REST CRUD + 列表/详情 Vue 页
  - migration_sales_training_doc.sql
- **RagflowClient** 增 `chatComplete` / `agentComplete` 方法（流式 SSE 解析）
- **三层 fallback 架构**（已废弃）：Agent → Chat → Java 5-analyst 兜底

### 修复
- RagflowClient SSE 流解析 bug：`messages` 数组字段（原误用 `question`）

---

## v9.5 — 对话式深度对标（自建 UI，已废弃）
日期：2026-05-23

### 新增（保留为备份代码，侧栏不挂）
- `ChatBenchmarkView.vue` 自建 ChatGPT 式聊天 UI
- `ChatBenchmarkController` + `ChatSessionService` + `FollowupAgent`
- `deep_benchmark_session` / `deep_benchmark_message` 表
- md-editor-v3 + mermaid 渲染

---

## v9 — 深度对标 H5（5 Analyst Agent）
日期：2026-05-22

### 新增
- 6 个 Agent 多智能体编排（5 analysts 并行 + 1 synthesizer）
- `DeepBenchmarkAgent` + `DeepBenchmarkService`
- `BenchmarkContext` / `AnalystResult` DTO
- SSE 进度推送
- `BenchmarkDataController` REST `/api/v1/benchmark/data?selfModel=X&competitorModels=Y&dimension=power|...|all`

---

## v8+ — 卡片专业化 + 按车型金融政策
日期：2026-05-15

### 新增
- KPI 横排（column_set 4 列：上市 / 价格 / 营销 / 销量）
- 销量对比警示（本品月销 < 竞品 ×2 时标红）
- `FinanceByModelView.vue` 独立页（按车型最新金融政策）
- `FinanceByModelController` REST
- `ImageProxyController` 图片代理（绕 Chrome ORB + sinaimg 防盗链）
- `AutohomeParamNormalizer`（60+ 个 `·` 占位符 → 真名映射，按 category 上下文消歧）

### 修改
- 飞书卡片标题：「竞品分析日报」→「**竞品动态**」
- 营销活动板块取消折叠（用户反馈）
- 删除「按车型最新金融政策」卡片板块（与 launch/price_finance 重复）

---

## v8 — OCR 图片管线 + 盖世销量
日期：2026-05-10

### 新增
- **图片 OCR**（`OcrEnrichmentService`）：qwen-vl-max-latest 视觉模型抽取官号海报政策文字
  - `weibo_note.pics` + `web_search_news.image_urls/image_ocr_text/ocr_ts` 字段
  - MediaCrawler patch 写 `pics`
- **盖世汽车销量**（`GasgooSalesScraper` + `GasgooSalesSyncService`）
  - `gasgoo_sales_record` 表
  - `autohome_series_config.gasgoo_series_id` 映射
  - URL: `m.gasgoo.com/qcxl/cxxl/{year}/{month}/{series_id}`
  - **关键**：从 `LocalDate.now().minusMonths(1)` 起算，绝不从当月开始
- `QwenAiService.chatWithVisionModelHttp` — OpenAI 兼容 vision 端点

### 修改
- 销量板块走结构化盖世数据（取代叙事官号销量帖）

---

## v7 — 销量权威化
日期：2026-04-30

### 新增
- `is_authoritative_sales` 字段（`official_account_config`）
- `findLatestAuthoritativeSalesByBrand` SQL（每品牌最新一条权威销量）
- 行业权威源 seed：乘联会 + 中汽协（占位 UID，需手补真值）
- `SALES_AUTHORITY_BRANDS` 白名单

---

## v4 — 官号一手数据
日期：2026-04-15

### 新增
- `official_account_config` 表 + 6 车企微博 UID seed
- `OfficialAccountCrawlerService`（creator 模式抓官号）
- `OfficialPostIngester`（weibo_note → web_search_news，publish_time 写入 add_ts）
- `OfficialAccountConfigView.vue` 管理页
- `CrawlerServiceClient.triggerCreatorCrawl`（v4 新增）

### 修改
- MediaCrawler patch 加 `max_count` 限抓最近 N 条（避免抓全量历史）
- `news_url_blacklist` 表 + 11+ pattern 黑名单过滤 4S 店 SEO 噪音

---

## v3 — 事件分类管线
日期：2026-04-01

### 新增
- `EventExtractionAgent`（qwen-turbo）4 类事件分类
- `web_search_news` 加 5 字段：event_type / importance / summary / models / extract_ts
- `NewsEventExtractionService` 批量分类 + 链式触发 OCR
- 4 类事件板块：launch / price_finance / campaign / sales_milestone

---

## v2 — 新闻驱动（已废弃）
日期：2026-03-15

### 早期版本，详见 git log

---

## 项目起点 v1
日期：2026-03-01

从 BettaFish（Python 舆情系统）重构：
- Python DailyBriefing → Java Multi-Agent
- DeepSeek → 通义千问
- PostgreSQL → MySQL
- pgvector → Milvus

---

## 升级注意事项

升级到 v9.7 需要：
1. 跑 `migration_sales_training_doc.sql`
2. 配 `FEISHU_APP_ID` / `FEISHU_APP_SECRET` / `FEISHU_OAUTH_REDIRECT`
3. 启动 `mcp-server/start.sh`（Python 3.11+）
4. RAGFlow UI 注册 `nev-monitor` MCP（SSE 模式）
5. 跑 `mcp-server/apply-ragflow-mobile-patch.sh`
6. 详见 `DEPLOYMENT.md`

升级 RAGFlow 镜像后需重跑 mobile patch 脚本。
