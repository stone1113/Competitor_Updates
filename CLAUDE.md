# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 构建与运行

### 后端 (Java 11 / Maven)
```bash
mvn clean package -DskipTests              # 构建全部 8 个模块
mvn compile -pl nev-admin -am              # 仅编译 admin 及其依赖
mvn package -DskipTests -pl nev-admin -am  # 打包单个模块（含依赖）
```

**⚠️ Maven 工作目录陷阱**：`mvn -pl nev-admin -am` **必须在项目根目录运行**。如果之前 `cd nev-admin-ui` 跑前端构建后忘记 `cd ..`，会报 `Could not find the selected project in the reactor: nev-admin`。

**⚠️ Java 11 限制**：不能用 `Stream.toList()`（Java 16+），改用 `.collect(java.util.stream.Collectors.toList())`。

### 前端 (Vue 3 / Vite)
```bash
cd nev-admin-ui
npm install
npm run dev      # 开发服务器，端口 3000，/api 代理到 localhost:8090
npm run build    # 生产构建，输出到 dist/
```

**⚠️ 前端 Docker 部署陷阱**：`nev-admin-ui/Dockerfile` 只 `COPY dist/`，**不在容器内 build**。改前端代码后必须：
```bash
cd nev-admin-ui && npm run build     # 在本地生成新 dist/
cd .. && docker compose up -d --build ui      # 复制进容器
```

### 爬虫服务 (Python 3.11+ / Playwright)
本地运行（不在 Docker 内跑），需 Mac 桌面环境（弹出 Chromium 扫码）：
```bash
cd crawler-service
./start-local.sh                    # 首次会建 .venv + 装依赖 + 装 Chromium（5-10 min）
nohup ./keepalive.sh > keepalive.log 2>&1 &   # 守护模式，crash 自动重启
```
- 服务监听 `http://0.0.0.0:8091`
- Java 容器经 `host.docker.internal:8091` 调用
- 改 Python 代码后：`pkill -f 'uvicorn server:app'`，keepalive 5 秒后拉起新进程

Windows 本地调试使用 `crawler-service/.venv-win`。crawler-service 启动时会读取项目根目录 `.env`，并把后端常用的 `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` 映射成 MediaCrawler 需要的 `MYSQL_DB_HOST/MYSQL_DB_PORT/MYSQL_DB_NAME/MYSQL_DB_USER/MYSQL_DB_PWD`。本机默认库名是 `nev_insight`，不要把数据库密码写进代码，放在 `.env` 或进程环境变量里。

### Docker（全栈启动）
```bash
docker compose up -d                    # 启动所有服务
docker compose up -d --build app ui     # 重新构建并重启后端和前端
docker compose logs -f nev-insight      # 查看后端日志
```

### 服务与端口
| 服务 | 端口 | 容器名 / 进程 |
|------|------|--------|
| 管理后台前端 (Nginx) | 3080 | nev-ui |
| 后端 API | 8090 | nev-insight |
| MySQL 8.0 | 3306 | nev-mysql |
| Redis | 6379 | nev-redis |
| Kafka | 9092 | nev-kafka |
| 爬虫服务 (本地) | 8091 | uvicorn (Mac 上) |
| RAGFlow UI（独立部署） | 80 | docker-ragflow-cpu-1（在 `/Users/anliwu/claude-pro/ragflow/docker/`）|
| RAGFlow HTTP API | 9380 | 同上容器（路径前缀 `/api/v1/`，需 Bearer token）|

## 架构概览

Maven 多模块单体应用（Spring Boot 2.7.18）+ Vue 3 前端 + Python 爬虫服务。系统用于监控汽车品牌竞品动态，按 4 类事件聚合（上市/价格/营销/销量），通过通义千问视觉模型对官号海报做 OCR 提取优惠政策。

### 模块依赖关系
```
nev-common          (ApiResponse、BaseEntity、PlatformEnum、异常处理)
    └─ nev-model    (实体、DTO、MyBatis-Plus Mapper)
        ├─ nev-collector    (BochaAI、PlatformQueryService UNION ALL、CrawlerServiceClient、官号抓取、盖世销量、Autohome 反爬还原)
        ├─ nev-knowledge    (Milvus 向量检索、DashScope Embedding)
        │   └─ nev-intelligence  (QwenAiService、6 个 Agent、OcrEnrichmentService、AutohomeKnowledgeSyncService)
        │       └─ nev-report    (DailyReportPipeline / CompetitorReportPipeline、飞书卡片 v2)
        └─ nev-scheduler    (XXL-JOB 任务处理器)

nev-admin = Spring Boot 主应用（REST 控制器），依赖 nev-report + nev-scheduler
nev-admin-ui = Vue 3 单页应用（**纯自研 UI 组件**，无 Element Plus），Nginx 托管，/api 代理到 nev-admin
crawler-service/  = Python + FastAPI + MediaCrawler，独立运行
```

### 关键设计模式

**Multi-Agent 管线**（`nev-intelligence/.../agent/`）：6 个 Agent（Sentiment、Competitor、SalesPitch、ReportAssembly、Briefing、**EventExtraction**）实现 `ReportAgent<I,O>` 接口。SentimentAgent 解析 LLM 输出兼容裸数组 `[...]` 和 `{key: [...]}` 两种格式。EventExtractionAgent 用 qwen-turbo 批量分 5 类事件。由 `DailyReportPipeline`（舆情）和 `CompetitorReportPipeline`（竞品）分别编排。

**跨平台 UNION ALL 查询**（`nev-collector/.../PlatformQueryService.java`）：动态构建 5 张平台表（xhs_note、douyin_aweme、bilibili_video、weibo_note、kuaishou_video）的联合查询。注意：`weibo_note` 没有 `title` 列（用 `content` 代替），`desc` 和 `time` 是 MySQL 保留字（需反引号转义），各平台时间戳单位不同（毫秒 vs 秒）。`queryBuzzUnion()` 同时按 `add_ts`（采集时间）和 publish-time 做 36h 双重窗口，避免拿到"近期采集到、但发布于很久以前"的旧帖。

**互动量加权公式**：`点赞×1 + 评论×5 + 分享×10 + 播放÷10 + 收藏×2`

**飞书卡片 v2**（`FeishuCardBuilder` / `CompetitorCardBuilder`）：必须用 `schema: "2.0"`、`card.body.elements`（不是 `card.elements`）、`config.width_mode: "fill"`（不是 `wide_screen_mode`）。Button v2 用 `behaviors: [{type: "open_url", default_url, pc_url, ios_url, android_url}]`，**不能**用 v1 的 `multi_url`（飞书会返 `code: 11246`）。`background_style` 合法值仅 `default/grey/blue/red/green/yellow/...`，**没有 `light_*` 变体**。`FeishuPushService.sendCard` 必须读 `body.code == 0` 才算成功 — 飞书拒卡时仍返 HTTP 200。

**实体规范**：核心实体继承 `BaseEntity`（自增 ID，addTs/lastModifyTs 由 `MyBatisMetaHandler` 自动填充）。`MetaHandler 只在字段为 null 时填充`，手动 `setAddTs(publishMs)` 可覆盖（OfficialPostIngester 用此把发布时间写入 add_ts）。

**API 响应封装**：所有 REST 端点返回 `ApiResponse<T>`，格式为 `{code: 200, message, data}`。前端 `useApi.js` 自动解包。

**MyBatis-Plus 分页**：必须注册 `MybatisPlusInterceptor`（`nev-admin/.../config/MybatisPlusConfig.java`），否则 `selectPage` 不生效，total 永远为 0。

**MySQL 8 窗口函数**：`ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)` 用于「每组最新一条」语义。已用于：`findLatestByModel`（盖世销量）、`findLatestAuthoritativeSalesByBrand`（官号销量）。最低 MySQL 8.0。

## 竞品动态子系统 (v8)

> v8 = v2-v7 累积迭代后的当前状态。v2（新闻驱动）→ v3（事件分类）→ v4（官号一手）→ v7（销量权威化）→ v8（标题改名 + OCR + 盖世销量）。

### 卡片标题：「竞品动态」（v8 改名）

`CompetitorCardBuilder` 卡片 header 标题 = `{brand} · {date} 竞品动态`（之前 v7 是「竞品分析日报」）。

### 4 类事件 + 销量板块

| 板块 | 数据源 | 关键过滤 |
|---|---|---|
| 🚀 新车上市 / 改款 | `web_search_news.event_type='launch'` | 36h 窗口；排除已进 💰 板块的 id（去重） |
| 💰 价格 & 金融政策 | `findFinanceCandidates` 聚合 launch+price_finance 中含「图含/售价/万元/优惠/补贴/0息/首付」字眼的官号帖 | 36h 窗口；**仅官号** (`source_tool LIKE '%_official'`) |
| 📣 营销活动 | `event_type='campaign'` | 36h 窗口 |
| 📈 销量 & 交付里程碑 | **结构化**：盖世汽车 `gasgoo_sales_record`（每车型最新月）；**叙事**：官号销量帖 | 不限时间窗 |
| ⚔️ 技术对标矩阵 | `autohome_spec`（按 vs_self_model 分多桌） | 卡片只列跳转按钮，详细数据在前端 |

**对标品牌白名单**（`BENCHMARK_BRANDS_ORDER`）：猛士 / 仰望 / 坦克 / 方程豹 / 问界 / 路虎。**销量板块额外允许**「乘联会」「中汽协」两个行业权威源（`SALES_AUTHORITY_BRANDS`）。

**去重双层**：
- L1: `dedupBySummary` 用 `brand+firstModel+eventType` key，「问界M9」「问界M9系列」会归一（`firstModel(models)` 去后缀「系列/版/款/车型」）
- L2: `dedupBySimilarity` 用字符 bigram Jaccard ≥ 0.25，跨媒体改写同事件归一；`STOP_BIGRAMS` 黑名单去掉「正式/上市/发布」高频词避免误伤

### 事件分类管线 (v3)

```
[每天 02:30 / 手动] WebNewsCollectorService → web_search_news (event_type=NULL)
                                            ↓
[每天 06/12/18/23 / extract-events] EventExtractionAgent (qwen-turbo)
                                            ↓ 写回 5 字段
                              event_type/importance/summary/models/extract_ts
                                            ↓ 链式
[NewsEventExtractionService.extractPending 末尾] OcrEnrichmentService.run(20)
                                            ↓ 对 price_finance + launch 官号有图未跑
[qwen-vl-max-latest] 提取图片 OCR 文字 → JSON {ocr_text, has_finance_policy, policy_summary}
                                            ↓ 写回
                  image_ocr_text / event_summary 加「图含：xxx」/ event_importance +2 / ocr_ts
```

**关键文件**：
- `EventExtractionAgent.java` — qwen-turbo prompt 含「Hashtag 不是动作」「品牌错位检测→spam」「官号 importance≥6」三条强信号规则
- `NewsEventExtractionService.java` — 批量 8 条/请求，末尾**链式触发** `OcrEnrichmentService.run`，失败不阻塞
- `news_url_blacklist` 表 + 11+ pattern 黑名单过滤 4S 店 SEO 噪音（`/dealer/`、`/4s/`、`/koubei/`、`autohome.com.cn/{dealer,4s,promo,spec,config}/` 等）

### 官号一手数据 (v4)

**抓取链路**：
```
[每天 cron / 手动] OfficialAccountCrawlerService.crawlAll
                  → CrawlerServiceClient.triggerCreatorCrawl
                  → Python /crawl?mode=creator&creator_ids=...
                  → MediaCrawler weibo creator 模式（已 patch max_count 限抓最近 N 条）
                  → asyncmy 写 weibo_note (含新字段 pics)

[5-10 分钟后 / 手动] OfficialPostIngester.ingestAll
                  → SELECT weibo_note 36h 内官号帖 + 新字段 pics
                  → 写 web_search_news (source_tool=weibo_official, image_urls=pics)
                  → 关键：把 publish_time 写入 add_ts（覆盖 MetaHandler 默认值），下游 36h 窗口按发布时间过滤
```

**`official_account_config` 表**（`/collector/official-accounts` 维护）：
- 6 车企微博官号 UID（猛士/仰望/坦克/方程豹/问界/路虎）+ 2 行业权威源占位（乘联会 TODO_cpca_wb、中汽协 TODO_caam_wb，需手工补真 UID）
- `is_authoritative_sales` 字段（v7）= 1 时表示销量数据权威源

### v8 OCR 图片管线

**DB 字段**（`migration_v8_ocr.sql`）：
- `weibo_note.pics TEXT` — MediaCrawler 提取 `mblog.pics[].large.url` 拼逗号串
- `web_search_news.image_urls TEXT` — `OfficialPostIngester` 透传
- `web_search_news.image_ocr_text TEXT` — vision LLM 输出的 OCR 文字 + 政策摘要
- `web_search_news.ocr_ts BIGINT` — 完成时间，NULL=未跑

**MediaCrawler patch**（`crawler-service/mediacrawler/`）：
- `store/weibo/__init__.py:update_weibo_note` — 在 `save_content_item` 字典构造时加 `pics` 字段
- `database/models.py:WeiboNote` — 加 `pics = Column(Text)` 列

**视觉模型调用**（`QwenAiService.chatWithVisionModelHttp`）：
- OpenAI 兼容 endpoint `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
- `messages[user].content` 是数组：`[{type:"text",text:...},{type:"image_url",image_url:{url:...}},...]`
- model = `qwen-vl-max-latest`（`DashScopeProperties.visionModel`）
- 与现有 `chatViaHttp` 共享 Bearer token 认证，仅 model + content 结构不同

**OCR 增强逻辑**（`OcrEnrichmentService`）：
- 触发条件：`event_type IN ('price_finance','launch')` + `source_tool LIKE '%_official'` + `image_urls` 非空 + `ocr_ts IS NULL`
- 每帖最多取前 4 张图（控成本）
- LLM 返回 JSON：`{ocr_text, has_finance_policy, policy_summary}`
- 写回：`image_ocr_text = ocr_text + "\n【政策】" + policy_summary`，`event_summary` 末尾追加「图含：xxx」（idempotent），`event_importance += 2`（封顶 10），`ocr_ts = now()`
- 失败/无政策也写 `ocr_ts` 避免重跑
- 触发：`POST /api/v1/collector/ocr-enrich?limit=N`，或链式（NewsEventExtractionService 末尾）

### 销量数据 (v7 + v8 盖世)

**v7 权威源筛选**：销量板块用 `findLatestAuthoritativeSalesByBrand` 取 `source_tool LIKE '%_official' + importance>=5` 的每品牌最新一条（不限时间窗）。

**v8 盖世汽车结构化销量**（`gasgoo_sales_record` 表）：
- URL: `https://m.gasgoo.com/qcxl/cxxl/{year}/{month}/{series_id}` — 纯静态 HTML 4 行 × 5 列表格
- `autohome_series_config.gasgoo_series_id` — 手工映射（已配 9 个对标车型，仅卫士110 未配）
- `GasgooSalesScraper`（`nev-collector/.../client/`）— RestTemplate + 正则解 td，无外部依赖
- `GasgooSalesSyncService.syncOne` — **从 `LocalDate.now().minusMonths(1)` 起算回看** N 个月（销量数据滞后一月，绝不从当月开始，否则只抓到 header 无 data）
- `findLatestByModel` SQL：`ROW_NUMBER() OVER (PARTITION BY model_name ORDER BY period_year DESC, period_month DESC)`，每车型最新一月
- REST：`POST /api/v1/gasgoo/sync` 全量、`POST /api/v1/gasgoo/sync/{configId}` 单车系
- 销量板块按 vs_self_model 分组的 markdown 表格（车型 | 月销 | 本年累计），本品 🔹 高亮

### 按车型金融政策 (v8+)

**前端独立页** `/competitor/finance-by-model`（侧栏「竞品 → 车型金融政策」）：
- 9 个对标车型卡片网格，按 vs_self_model 分组
- 每张卡片显示该车型最新 1-N 条 launch/price_finance 官号帖（不限时间窗）
- 缩略图 + 点击放大 + 「展开 OCR 文字」detail
- REST：`GET /api/v1/finance/by-model?model=豹8&limit=5` 单车型；`GET /api/v1/finance/all-models?limit=3` 全 9 车型一次查
- 飞书卡片**不再展示**「按车型最新金融政策」板块（v8 删除，避免与 launch/price_finance 重复），只在前端独立页

**`FinanceByModelController.stripBrandPrefix`** — 把「方程豹豹5」剥成「豹5」用于模糊匹配，提升命中率。

### 图片代理（绕浏览器 ORB + sinaimg 防盗链）

**`ImageProxyController`** 提供 `GET /api/v1/proxy/image?url=...`：
- 6 域白名单（`sinaimg.cn/qpic.cn/douyinpic.com/byteimg.com/xhscdn.com/bdstatic.com`）按 host 后缀匹配
- 按 host 自动加正确 Referer：sinaimg → `m.weibo.cn`，douyinpic → `douyin.com`，xhscdn → `xiaohongshu.com`
- 响应加 `Cross-Origin-Resource-Policy: cross-origin` + `Cache-Control: public, max-age=3600`
- 前端 `<img src="/api/v1/proxy/image?url=encoded">` 代替直链，绕 Chrome ORB（Opaque Response Blocking）拦截

### Autohome 参数还原 (v8+)

**Autohome 反爬机制**：配置页 `var config = {...}` 含完整 JSON，但部分中文字符被替换为 `<span class='hs_kwXX_configZa'></span>` 字体占位（font-face 还原）。`crawler-service/autohome_scraper.py` 把 span 替换为 `·` 占位字。

**`AutohomeParamNormalizer`**（`nev-collector/.../util/`）：60+ 个 `·` 占位 pattern → 真名映射表，按 `category` 上下文消歧（如 `·(mm)` 在发动机 → 缸径，在车身 → 高度）。**`AutohomeController.compareSpecs` 输出前调用** normalize + 过滤全空行（值全是 ""/`-`/`·` 的参数不展示）。

参数**值**里仍有 `·` 占位（如「非·」「·制造厂」），各车系真值不同，**未做映射**。需 v9 解 woff2 字体或人工逐条补。

### 调试技巧

**Chrome DevTools MCP 截图陷阱**：take_screenshot 在页面含大量 sinaimg 图片时会**协议超时**（即使图片代理正常）。改用 `take_snapshot`（a11y 树文本）+ `list_network_requests`（图片是否 200 OK）验证渲染。

**应用数据库迁移**（必须 `--default-character-set=utf8mb4` 否则中文乱码）：
```bash
docker exec -i nev-mysql mysql -uroot -pnev123456 --default-character-set=utf8mb4 \
  nev_insight < nev-admin/src/main/resources/db/<FILE>.sql
```

**重启 crawler-service**（让 Python 代码改动生效）：
```bash
pkill -f 'uvicorn server:app'   # keepalive 5 秒后自动拉起
```

### 社交平台爬虫架构

**整体方案**：复用 NanmiCoder/MediaCrawler，独立 Python 服务，HTTP 触发，直写 MySQL。Java 不重写爬虫。

```
Vue UI → Java (nev-admin) → HTTP → Python (FastAPI 8091) → subprocess → mediacrawler/main.py → asyncmy → nev-mysql
                                                                                              ↑
                                                                          PlatformQueryService 直查（共用同一个 DB）
```

**关键文件**：
- `crawler-service/server.py` — FastAPI 端点：`POST /crawl`（支持 `mode=search|creator`，v4 新增）、`POST /login`、`GET /tasks/{id}`、`POST /scrape-autohome`
- `crawler-service/runner.py` — 任务执行 + cookie 失效检测 + 自动重登 + 重试
- `crawler-service/mediacrawler/` — NanmiCoder MediaCrawler 副本（已 patch：写 `brand_name` 字段、`pics` 字段；多处 `goto` 超时调到 120s；快手禁用代理 + ignore HTTPS；weibo `get_all_notes_by_creator_id` 加 `max_count` 限抓）
- `nev-collector/.../client/CrawlerServiceClient.java` — `triggerCreatorCrawl` 是 v4 新增
- `nev-collector/.../service/OfficialAccountCrawlerService.java` — 官号 creator 模式抓
- `nev-collector/.../service/SocialCrawlerService.java` — 关键词搜索模式抓（**不用 @Async**——同类调用不走代理失效）

**Cookie 失效检测**（`runner.py:_looks_like_auth_failure`）：
- **强信号**（任何时候）：`Login state result: false`、`check login state failed`、`未登录`、`请登录`
- **正则边界**：`\b401\b` / `\b403\b` 避免匹配 19 位评论 ID 子串
- **弱信号**（仅快速失败 + 非零退出）：`timeout` 关键词
- **不收 `DataFetchError`** 和 `登录`（"登录成功"会误命中）

**自动重登流程**（`runner.py:_run_task`）：
- cookie 模式失败 → `WAITING_LOGIN` → 触发 qrcode 任务（弹浏览器扫码）→ 等最多 `LOGIN_WAIT_SEC`（默认 300s）→ 登录成功**自动重跑原任务一次**
- `_wait_for_login` 监测日志里的 `Login status confirmed` / `登录成功` 早退
- 5 分钟冷却防同一平台连续触发

**平台 quirks**：
- Windows 微博登录：优先用普通 Chrome 打开 `crawler-service/mediacrawler/browser_data/wb_user_data_dir` 这个用户目录完成手机号登录；验证 `https://m.weibo.cn/api/config` 返回 `login:true` 后关闭 Chrome，再让 MediaCrawler 复用同一 profile。验证码在 Playwright 窗口里可能点不动，不要反复重试。
- 二维码：`crawler_util.py:find_login_qrcode/show_qrcode` 会尝试保存放大的 `weibo_login_qrcode.png` 供扫码；若页面验证码拦截，仍以普通 Chrome profile 登录为准。
- B站：流程内会写 `bilibili_up_info`、`bilibili_contact_info`、`bilibili_up_dynamic`、`xhs_creator`、`dy_creator`、`weibo_creator` 等辅助表。**这些表必须建**，否则爬第一条后 SQL 报错跳出。见 `db/migration_creator_tables.sql`
- 快手：TLS 严苛，遇 Clash/V2Ray 等 MITM 时会报 `ERR_SSL_VERSION_OR_CIPHER_MISMATCH`。已加 `--no-proxy-server` + `--ignore-certificate-errors` + `ignore_https_errors=True`，runner 也清掉 HTTP_PROXY env。**仍失败时需用户关闭 Clash TUN 模式或加 DIRECT 规则**

**微博官号本地链路**：
1. 启动 8091 crawler-service，并确认日志里 `mysql env` 指向 `127.0.0.1:3306/nev_insight` 且 `pwd=SET`。
2. 触发 Java 端 `POST /api/v1/official-accounts/crawl-now?platform=wb`，Java 会按 `official_account_config` 调 crawler-service 的 creator 模式。
3. 爬取成功后执行 `POST /api/v1/official-accounts/ingest-now`，把 `weibo_note` 36h 内官号帖写入 `web_search_news(source_tool=weibo_official)`。
4. 需要卡片事件类型时再执行 `POST /api/v1/collector/extract-events?maxBatches=N`；OCR 会在事件抽取末尾链式触发，或手动 `POST /api/v1/collector/ocr-enrich?limit=N`。
5. 注意 `weibo_note.create_time` 是秒，`web_search_news.add_ts` 是毫秒；OfficialPostIngester 会把发布时间写进 `add_ts` 供 36h 窗口使用。

### 数据库

**初始 DDL：** `nev-admin/src/main/resources/db/schema.sql`（Docker MySQL 启动时自动加载）。

**增量迁移脚本（按时间顺序应用）：** `nev-admin/src/main/resources/db/`
- `migration_platform_tables.sql` — 5 内容表 + 5 评论表（MediaCrawler ORM 字段对齐）
- `migration_creator_tables.sql` — 7 张辅助表
- `migration_brand_keyword.sql` / `migration_keyword_v2.sql` — 品牌关键词配置 + category 字段
- `migration_crawler_schedule.sql` — 爬虫定时任务配置
- `migration_autohome.sql` / `migration_autohome_v2.sql` — 汽车之家车系白名单 + 参数表
- `migration_event_v3.sql` — `web_search_news` 加 5 个事件字段 + URL 黑名单表
- `migration_official_account.sql` — 官方账号配置 + 6 微博 UID seed
- `migration_sales_authority_v7.sql` — is_authoritative_sales 字段 + 2 行业权威账号 seed
- `migration_v8_ocr.sql` — `weibo_note.pics` + `web_search_news.image_urls/image_ocr_text/ocr_ts`
- `migration_gasgoo_sales.sql` — `autohome_series_config.gasgoo_series_id` + `gasgoo_sales_record` 表

### 配置说明

后端配置位于 `nev-admin/src/main/resources/application.yml`，敏感信息通过环境变量注入：

**核心**：
- `DASHSCOPE_API_KEY` — 阿里云通义千问（含 v8 视觉模型 qwen-vl-max-latest）
- `DASHSCOPE_VISION_MODEL` — 默认 `qwen-vl-max-latest`（OCR 用）
- `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` — MySQL（**必须 8.0+** 支持窗口函数）
- `BOCHA_API_KEY` / `BOCHA_BASE_URL` — BochaAI 搜索引擎（默认 `https://api.bocha.cn/v1/ai-search`）
- `FEISHU_TEST_WEBHOOK_URL` / `FEISHU_PROD_WEBHOOK_URL` / `FEISHU_COMPETITOR_WEBHOOK_URL` — 飞书机器人 Webhook
- `CRAWLER_BASE_URL` — Python 爬虫服务地址（默认 `http://host.docker.internal:8091`）
- `CRAWLER_DISABLED_PLATFORMS` — 临时屏蔽平台（默认 `xhs`）

**RAGFlow + 竞品深度对标**：
```yaml
ragflow:
  base-url: ${RAGFLOW_BASE_URL:http://host.docker.internal:9380}
  ui-base-url: ${RAGFLOW_UI_BASE_URL:http://localhost}
  api-key: ${RAGFLOW_API_KEY:}
  kb-id: ${RAGFLOW_KB_ID:}
  deep-analysis-shared-id: ${RAGFLOW_DEEP_ANALYSIS_SHARED_ID:}
competitor:
  cron: ${COMPETITOR_REPORT_CRON:0 30 8 * * ?}
  autohome-sync-cron: ${AUTOHOME_SYNC_CRON:0 0 3 ? * MON}
crawler:
  news-cron-self-competitor: ${NEVINSIGHT_CRAWLER_NEWS_CRON_SELF_COMPETITOR:0 0 6,12,18,23 * * ?}
  news-cron-industry:        ${NEVINSIGHT_CRAWLER_NEWS_CRON_INDUSTRY:0 30 6 * * ?}
frontend:
  base-url: ${FRONTEND_BASE_URL:http://localhost:3080}
```

社交平台 cookie 字符串（可选；空则首次走 qrcode）：`XHS_COOKIE_STR` / `DOUYIN_COOKIE_STR` / `WEIBO_COOKIE_STR` / `BILI_COOKIE_STR` / `KS_COOKIE_STR`

`BrandConfigProperties`（`nev-intelligence/.../config/`）的 `searchKeywords`、`competitorBrands` 等中文 key 的 Map 不能通过 YAML 绑定（Spring Boot 会将中文 key 解析为数字索引）。改用 `@PostConstruct initDefaults()` 在 Java 中初始化默认值。

### 前端（已无 Element Plus）

**完全自研 UI 组件**位于 `nev-admin-ui/src/components/ui/`：18 个组件（Button/Input/Select/Date/Drawer/Dialog/Popconfirm/Table/Pagination/Tag/Switch/Tabs/Card/Field/Divider/Toast/UDialog）。

**设计系统**：`src/styles/{tokens,fonts,reset}.css` — FT/编辑感美学（Fraunces 衬线 + General Sans + JetBrains Mono；oxblood 红色 `#c0392b` 单色调；近黑 `#171614` ink；米白 `#fbf9f4` 底）。

**侧栏分组** (`AppSidebar.vue`)：出版 / 竞品 / 采集 / 知识 / 系统。

**核心页面**：
- `competitor/` — `EventBrowserView`（事件浏览）/ `AutohomeConfigView`（车系配置）/ `AutohomeSpecView` + `BenchmarkH5View`（技术对标矩阵）/ **`FinanceByModelView`（按车型金融政策，v8+ 新增）** / `DeepAnalysisView`（iframe 嵌入 RAGFlow）
- `collector/` — `DataSourceView` / `BrandKeywordView`（含 category Tabs）/ `OfficialAccountConfigView`（官号管理，v4）/ `SocialCrawlerView`
- `daily-report` / `knowledge` / `talking-point` / `prompt` / `report-console` / `system/url-blacklist`

### DashScope SDK 注意事项

`dashscope-sdk-java` 依赖内置了 `slf4j-simple`，与 Logback 冲突。已在父 POM 中通过 `<exclusions>` 排除。

容器 docker network 下 DashScope SDK 长连接可能断流。竞品分析 Agent 大 prompt + 慢响应场景 → 改用 OpenAI 兼容 HTTP 端点（`chatWithReportModelHttp` / `chatWithScreeningModelHttp` / **`chatWithVisionModelHttp`** v8 新增）。

### BochaAI API 格式

请求体 `{query, freshness, stream, answer}`，响应 `{code: 200, messages: [{role, type, content_type, content}]}`。网页搜索结果在 `content_type="webpage"` 的 message 中，`content` 是 JSON 字符串内含 `{webSearchUrl, value: [{name, url, snippet}]}`。

`freshness` 当前硬编码 `"oneDay"`（约 24h），不支持配置。**Bocha API 不返 `published_date`，无法按发布时间过滤新闻** — 以 `add_ts` 为近似（含偏差但可用）。

### 参考项目

本项目重构自 `/Users/anliwu/claude-pro/BettaFish`（舆情监控系统），主要变化：Python DailyBriefing → Java Multi-Agent、DeepSeek → 通义千问、PostgreSQL → MySQL、pgvector → Milvus。BettaFish 的 `.env` 中保存了所有 API Key，`mediacrawler/` 子目录是当前 `crawler-service/mediacrawler/` 的来源。

### 语言要求

所有对话和文档使用中文。

---

## v9 系列：智能竞品对标（RAGFlow Agent + MCP + H5）

> v9 是 v8 的横向扩展 — 在「广度感知（卡片日报）」基础上加「深度对标（对话式 H5）」。
> v8 的 4 类事件卡片管线保留不动，v9 是新增独立子系统。

### v9.7 当前架构

```
┌─────────────────────────────────────────────────────────────┐
│  飞书机器人推送日报（CompetitorCardBuilder v8+）              │
│       ↓ 卡片底部按钮 「🤖 智能竞品对标」                       │
└──────────────┬──────────────────────────────────────────────┘
               │
               ↓
   ┌───────────────────────────────────────────┐
   │  m-monitor H5 独立页 /h5/benchmark         │
   │  (BenchmarkH5StandaloneView.vue)          │
   │  - 强制飞书 OAuth 登录                      │
   │  - 顶栏自动隐藏，iframe 全屏              │
   │  - 浮动用户徽章（右上角，可展开退出）       │
   └───────────────┬───────────────────────────┘
                   │ 内嵌 iframe
                   ↓
   ┌───────────────────────────────────────────┐
   │  RAGFlow Agent Share Page                 │
   │  /agent/share?shared_id=X&auth=Y&         │
   │     visible_avatar=1&data_user_id={openId}│
   │  (CSS mobile patch 已注入 RAGFlow 容器)    │
   └───────────────┬───────────────────────────┘
                   │ ReAct loop 调 MCP tools
                   ↓
   ┌───────────────────────────────────────────┐
   │  nev-monitor-mcp (Python, port 8092)      │
   │  4 个工具：                                 │
   │  - query_autohome_spec  (拉真实参数)       │
   │  - query_social_voc     (拉 KOL 评测)      │
   │  - get_customer_persona (画像模板)         │
   │  - create_sales_training_doc (写文档)      │
   └───────────────┬───────────────────────────┘
                   │ HTTP（trust_env=False 绕代理）
                   ↓
   ┌───────────────────────────────────────────┐
   │  m-monitor REST API (port 8090)           │
   │  - GET /api/v1/benchmark/data             │
   │  - POST /api/v1/sales-training-doc        │
   │  - 直查 weibo_note/douyin_aweme/bilibili  │
   └───────────────────────────────────────────┘
```

### 关键组件

**1. nev-monitor-mcp**（Python MCP server）

位置：`mcp-server/server.py`，启动 `mcp-server/start.sh`

- 基于 FastMCP，SSE transport 跑在 `:8092/sse`
- RAGFlow 容器经 `host.docker.internal:8092/sse` 接入
- **⚠️ 关键坑**：FastMCP 默认 DNS rebinding protection 拒非 localhost host header；用 `TransportSecuritySettings(enable_dns_rebinding_protection=False)` 关掉
- **⚠️ 第二坑**：httpx 默认走系统代理；macOS 上 Clash/WARP 会把内网请求转 cloudflare 返 400；所有 `httpx.Client(...)` 必须 `trust_env=False`
- 启动 Python ≥3.11（mcp SDK 要求）：`python3.11 -m venv .venv`

**2. RAGFlow MCP 注册**

RAGFlow v0.25 UI → 设置 → MCP → 添加：
- Name: `nev-monitor`
- Type: **SSE**（不是 streamable HTTP — RAGFlow client 对老 SSE 兼容性更好）
- URL: `http://host.docker.internal:8092/sse`

成功时 RAGFlow 显示 4 个工具。Test failed 报「Connection failed (possibly due to auth error)」时**不一定是 auth 问题** — RAGFlow 把所有连接异常都包装成这句话，看 docker logs 找真实 trace。

**3. RAGFlow Agent Canvas**

Canvas 结构（极简）：
```
[开始] → [检索] (KB 优先) → [智能体] (绑 nev-monitor MCP)
```

智能体节点：
- 模型：qwen-max@Tongyi-Qianwen
- 挂工具：`query_autohome_spec`、`query_social_voc`（其它按需）
- System prompt 双路径设计：
  - 路径 A（单车查询）：「猛士M817 接近角多少」→ 直接小段回答
  - 路径 B（对标分析）：「M817 对标」→ 完整 8 章节 Markdown
- KB 优先：`{Retrieval_0@chunks}` 含相关内容时禁用 MCP（省成本）

完整 prompt 见 `docs/RAGFlow-深度对标-Agent-配置指南.md`。

**4. iframe 嵌入 URL 格式**

```
http://localhost/agent/share?
  shared_id={agent_canvas_id}
  &from=agent                    # chat 是 Chat Assistant；agent 是 Canvas
  &auth={beta_token}             # RAGFlow UI「嵌入网页」拿到
  &theme=light
  &locale=zh
  &visible_avatar=1              # 反逻辑！=1 才隐藏头像（移动端节省空间）
  &data_user_id={openId}         # 飞书 openId 传递（未来 per-user session）
```

CompetitorReportController.deepAnalysisUrl() 端点拼这个 URL，前端 H5 拉走。

**5. 飞书 OAuth（H5 强制登录）**

`FeishuAuthController` 三端点：
- `GET /api/v1/feishu-auth/config` — 给前端：是否要求登录 + login URL
- `GET /api/v1/feishu-auth/login` — 302 跳飞书授权页
- `GET /api/v1/feishu-auth/callback?code=` — 用 code 换 user_access_token 再换用户信息

需要的 env：
```
FEISHU_APP_ID=cli_xxxxxx
FEISHU_APP_SECRET=xxx
FEISHU_OAUTH_REDIRECT=http://localhost:3080/h5/benchmark
FEISHU_REQUIRE_LOGIN=true
```

飞书后台配置：
- 安全设置 → 重定向 URL 加 `FEISHU_OAUTH_REDIRECT` 的值
- 权限管理 → 勾选 `获取用户的基本信息`（最小集）
- 应用发布（内部测试或正式审核）

**6. H5 移动端适配**

两层优化：
- m-monitor 包装层（`BenchmarkH5StandaloneView.vue`）：iframe 模式下顶栏完全隐藏，用户信息浮动到右上角徽章
- RAGFlow share 页（跨域，无法注入 CSS）：用 `mcp-server/apply-ragflow-mobile-patch.sh` patch RAGFlow 容器，把 mobile CSS link 注入 `/ragflow/web/dist/index.html`

**⚠️ patch 在 RAGFlow 容器内**：容器 `docker compose down && up` 重建后会丢失，需重跑脚本。

### v9 阶段历程

- **v9 / v9.5 / v9.6**（已废弃）：自建 ChatBenchmarkView（Vue 聊天 UI 调 RAGFlow Chat API），保留代码作备份；侧栏入口已撤
- **v9.7**（当前）：统一 H5 + RAGFlow Agent iframe + MCP 工具调用 + 飞书 OAuth

### 数据流（v9.7 跑通后）

用户操作：
1. 飞书收到日报卡片，点「🤖 智能竞品对标」按钮
2. 跳 `/h5/benchmark` → 显示飞书登录
3. 登录后回调 → localStorage 缓存用户身份 → iframe 加载 RAGFlow Agent
4. 用户在 RAGFlow 聊天框输入 `M817 对标`
5. Agent ReAct 循环：
   - 调 `{Retrieval_0@chunks}` 看 KB 有没有
   - KB 空 → 调 `query_autohome_spec` 拿真参数
   - 调 3 次 `query_social_voc` 拿 KOL 评测
   - LLM 综合写 Markdown 8 章节
6. RAGFlow 流式输出到用户

### v9 关键文件清单

```
nev-admin/.../FeishuAuthController.java        OAuth 三端点
nev-admin/.../CompetitorReportController.java   /deep-analysis-url 加 userId 参数
nev-admin/.../SalesTrainingDocController.java   备用文档保存端点（当前 Agent 不用）

nev-admin-ui/src/views/h5/BenchmarkH5StandaloneView.vue   H5 独立页
nev-admin-ui/src/router/index.js                 /h5/benchmark 路由（在 AppLayout 之外）

mcp-server/server.py                             nev-monitor MCP（FastMCP + SSE）
mcp-server/start.sh                              启动脚本（自动建 venv）
mcp-server/ragflow-mobile-patch.css              移动端 CSS override
mcp-server/apply-ragflow-mobile-patch.sh         注入 RAGFlow 容器脚本

docs/RAGFlow-深度对标-Agent-配置指南.md           Agent system prompt + Canvas 配置
DEPLOYMENT.md                                    完整部署流程（v9.7 含 MCP + RAGFlow + 飞书）
```

### v9 调试命令速查

```bash
# MCP server 状态
tail -f mcp-server/mcp.log
lsof -nP -iTCP:8092 -sTCP:LISTEN

# RAGFlow 容器内调 m-monitor API
docker exec docker-ragflow-cpu-1 sh -c "curl -s 'http://host.docker.internal:8090/api/v1/benchmark/data?selfModel=猛士M817&competitorModels=方程豹豹5&dimension=all' | head -c 300"

# RAGFlow MCP 真实错误（前端只显示通用消息）
docker logs docker-ragflow-cpu-1 --since 5m 2>&1 | grep -B2 -A20 'Exception\|Traceback' | tail -40

# 重新应用 RAGFlow mobile CSS patch（容器重建后）
mcp-server/apply-ragflow-mobile-patch.sh

# 触发推送日报（验整链路）
curl -X POST 'http://localhost:8090/api/v1/competitor-report/generate?push=true'
```
