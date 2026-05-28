# m-monitor · 猛士竞品监控与对标系统

> 服务于猛士汽车竞品分析团队的端到端监控 + 对标平台
> 当前版本：**v9.7**（含 RAGFlow Agent + MCP + 飞书 OAuth + H5）

---

## 系统能力

**广度感知**（卡片日报）— 飞书每日推送 4 类竞品事件
- 🚀 新车上市 / 改款
- 💰 价格 & 金融政策（含图片 OCR 抽取优惠条款）
- 📣 营销活动
- 📈 销量 & 交付里程碑（盖世汽车结构化数据）
- ⚔️ 技术对标矩阵（汽车之家参数）

**深度对标**（H5 智能体）— 对话式技术分析
- 双线对标：M817 线（家用/增程主流）+ 917 线（高端越野旗舰）
- 单车技术查询：「猛士M817 接近角多少」→ 直接回答
- 完整对标报告：「M817 对标」→ 8 章节 Markdown
- 数据策略：知识库优先 → autohome 实时参数兜底 → 社交 KOL 补技术实测
- 飞书 OAuth 强制登录，per-user 上下文

---

## 技术栈

```
┌──────────────────────────────────────────────────────────────┐
│  飞书机器人卡片 / 飞书移动端 H5                                  │
└──────────────────────────────────────────────────────────────┘
                          ↑
       ┌─────────────────────────────────────────┐
       │  nev-admin-ui (Vue 3 + Vite + 自研 UI)  │
       │  - /h5/benchmark 独立 H5 全屏页          │
       │  - /competitor/* 后台运营页              │
       └────────────────┬────────────────────────┘
                        │  /api
       ┌─────────────────────────────────────────┐
       │  nev-admin (Spring Boot 2.7 / Java 11)  │
       │  - 8 个 Maven 子模块                     │
       │  - 6 个 Agent 多智能体编排                │
       └────────────┬───────────┬────────────────┘
                    │           │
        ┌───────────┘           └──────────┐
        ↓                                  ↓
  MySQL 8 / Redis / Kafka          通义千问 / BochaAI

横向集成：
- RAGFlow（独立部署）— 知识库 + Agent Canvas
- nev-monitor-mcp（Python FastMCP）— 给 RAGFlow Agent 提供数据工具
- crawler-service（Python + Playwright + MediaCrawler）— 社交平台抓取
```

---

## 快速开始

详细部署见 **`DEPLOYMENT.md`**。

最短路径（已有 Docker + Maven + Node + Python 3.11）：

```bash
# 1. 配置
cp .env.example .env
vim .env  # 填 DASHSCOPE / BOCHA / FEISHU 等 key

# 2. 启动后端 + 数据库
docker compose up -d

# 3. 应用迁移脚本（按顺序）
# 见 DEPLOYMENT.md §2.3

# 4. 前端
cd nev-admin-ui && npm install && npm run build && cd ..
docker compose up -d --build ui

# 5. 爬虫服务（本地 Mac）
cd crawler-service && ./start-local.sh

# 6. MCP server（v9.7 智能对标用）
cd ../mcp-server && ./start.sh

# 7. RAGFlow Agent 配置
# 见 docs/RAGFlow-深度对标-Agent-配置指南.md

# 8. 飞书 OAuth 配置
# 见 DEPLOYMENT.md §7
```

验证：
```bash
curl -X POST 'http://localhost:8090/api/v1/competitor-report/generate?push=true'
# 飞书应收到「猛士汽车 · {date} 竞品动态」卡片
```

---

## 文档导航

| 文档 | 用途 |
|---|---|
| [README.md](README.md) | 你正在看的入口 |
| [CLAUDE.md](CLAUDE.md) | 给 Claude Code 用的架构上下文（开发必读） |
| [DEPLOYMENT.md](DEPLOYMENT.md) | 完整部署手册（运维必读） |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更历史 |
| [docs/竞品动态-业务方案.md](docs/竞品动态-业务方案.md) | 业务方案 + 板块设计依据 |
| [docs/RAGFlow-深度对标-Agent-配置指南.md](docs/RAGFlow-深度对标-Agent-配置指南.md) | RAGFlow Agent Canvas 完整配置 |

---

## 模块结构

```
m-monitor/
├── pom.xml                              Maven 父 POM
├── docker-compose.yml                   全栈编排
├── .env.example                         环境变量模板
│
├── nev-common/                          基础（ApiResponse / BaseEntity / 异常）
├── nev-model/                           实体 / DTO / Mapper
├── nev-collector/                       数据采集（Bocha / 平台 / Autohome 反爬）
├── nev-knowledge/                       Milvus 向量 / Embedding
├── nev-intelligence/                    6 个 Agent + LLM 服务
├── nev-report/                          报告管线 + 飞书卡片
├── nev-scheduler/                       XXL-JOB 任务处理器
├── nev-admin/                           Spring Boot 主应用 + REST
│
├── nev-admin-ui/                        Vue 3 前端
│   ├── src/views/competitor/            竞品相关页
│   ├── src/views/collector/             采集运维页
│   ├── src/views/h5/                    H5 独立全屏页（v9.7）
│   └── src/components/ui/               18 个自研 UI 组件（无 Element Plus）
│
├── crawler-service/                     Python 社交平台爬虫
│   ├── server.py                        FastAPI 触发端
│   ├── runner.py                        任务执行 + cookie 失效检测
│   └── mediacrawler/                    NanmiCoder MediaCrawler 副本（含 patch）
│
├── mcp-server/                          v9.7 — nev-monitor MCP server
│   ├── server.py                        FastMCP + 4 工具
│   ├── start.sh                         一键启动
│   ├── ragflow-mobile-patch.css         RAGFlow 移动端 CSS override
│   └── apply-ragflow-mobile-patch.sh    注入容器脚本
│
└── docs/                                业务方案 + 配置指南
```

---

## 端口约定

| 端口 | 服务 |
|---|---|
| 3080 | nev-ui（前端 Nginx） |
| 8090 | nev-insight（后端 Spring Boot） |
| 3306 | MySQL |
| 6379 | Redis |
| 9092 | Kafka |
| 8091 | crawler-service（本地 Mac） |
| 8092 | nev-monitor-mcp（本地 Mac） |
| 80 / 9380 | RAGFlow UI / API（独立部署） |

---

## 数据采集来源

| 来源 | 用途 | 实现 |
|---|---|---|
| 微博 / 抖音 / 小红书 / B站 / 快手 | 社交舆情 | MediaCrawler |
| 微博官方账号 | 官号一手 + OCR 海报 | crawler creator 模式 |
| 汽车之家 | 车系参数 | autohome_scraper.py（字体还原） |
| 盖世汽车 | 月度销量 | RestTemplate 静态 HTML |
| BochaAI | 通用新闻 | HTTP 搜索 API |
| 知识库（RAGFlow KB） | 内部技术文档 | 上传 PDF/xlsx |

---

## 关键设计原则

1. **数据先入库再分析** — 所有抓取数据落 MySQL，Agent 直查表（速度 + 稳定性）
2. **实时爬作为兜底** — autohome 等只在 KB 无数据时调用
3. **多 Agent 编排** — 6 个 ReportAgent 接口实现（Sentiment / Competitor / SalesPitch / ReportAssembly / Briefing / EventExtraction）
4. **飞书卡片 v2 schema** — 严格按 lark_md + behaviors[open_url]，避开 v1 兼容陷阱
5. **跨平台 UNION ALL** — PlatformQueryService 动态查 5 张平台表（含表保留字转义 / 时间单位差异处理）

详细架构和陷阱见 `CLAUDE.md`。

---

## 升级

- 单次升级：`git pull` → 跑 migrations → 重 build → 重启
- v9.7 升级路径：见 [CHANGELOG.md](CHANGELOG.md) 末尾

---

## 语言

所有代码注释、文档、UI 文案均**中文**。
