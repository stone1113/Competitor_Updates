# RAGFlow Agent 「智能竞品对标」配置指南（v9.7 最新）

> **当前生产配置** — 单智能体 + 知识库 + MCP，最简架构。
> v9.6 多节点 Invoke 架构见同目录旧文档（保留参考）。

---

## 一、整体架构

```
[开始] → [检索] → [智能体]
                    ↑
              挂 2 个 MCP 工具：
              - query_autohome_spec
              - query_social_voc
```

**为什么是单智能体不是多节点编排**：
- RAGFlow v0.25 智能体节点天生支持 ReAct 工具循环
- 单节点让 LLM 根据用户意图自主决定调哪个工具
- 不再需要画 5 个 Invoke 节点 + 5 个 Generate
- 配置成本从 ~2 小时降到 ~20 分钟

---

## 二、Canvas 搭建（10 分钟）

### Step 1 — 新建 Agent

1. RAGFlow → Agent → **+ Create agent** → Blank
2. 名字：`猛士竞品技术对标 v9.7`
3. 进 Canvas

### Step 2 — 开始节点

点画布上「开始」节点，**Opening greeting** 填：

```
👋 我是猛士竞品技术对标 Agent

🔹 我能做两件事：

【A】单车技术查询
- "猛士M817 接近角多少"
- "917 的电池续航如何"
- "介绍下猛士M817"
→ 一句话/小段直答

【B】双线竞品对标（8 章节完整报告）

▸ M817 线（家用 / 增程主流）
  本品：猛士M817 2026款 增程版
  竞品：方程豹豹5 · 问界M8 REEV

▸ 917 线（高端越野旗舰）
  本品：猛士917 2026款 增程版
  竞品：仰望U8 2024款 越野玩家版 · 坦克700新能源 2026款 Hi4-Z 极致版

→ 输入「M817 对标」或「917 对标」即可启动

💡 数据策略：知识库优先 → autohome 实时参数兜底 → 社交 KOL 补技术实测
```

### Step 3 — 加「检索」节点

1. hover 开始节点输出口 → 点圆点 → 展开「基础」分组
2. 选 **「检索」**
3. 配置：

| 字段 | 值 |
|---|---|
| 知识库 | 勾选「**竞品分析**」 |
| Top N | `8` |
| 相似度阈值 | `0.2` |
| 关键词权重 | `0.5` |
| 空响应 | **留空**（关键 — 让 Agent 自己判断空与非空） |

**📌 记下节点 ID**（点节点看右上角），后面 system prompt 要引用，如 `Retrieval_0`。

### Step 4 — 加「智能体」节点

1. hover 检索节点输出口 → 点圆点 → 展开「基础」分组
2. 选 **「智能体」**
3. 配置如下 5 项

### Step 5 — 智能体配置

#### 5.1 模型

`qwen-max@Tongyi-Qianwen`（⚠️ 不要用 qwen-turbo — ReAct 多轮工具调用需要强模型）

#### 5.2 ⭐ 挂载 MCP 工具

右侧 panel → Tools → + Add → 选 `nev-monitor` MCP → 勾选 2 个：

- ✅ `query_autohome_spec`
- ✅ `query_social_voc`
- ❌ `get_customer_persona`（不需要）
- ❌ `create_sales_training_doc`（不需要 — 直接输出聊天框）

#### 5.3 其他参数

| 字段 | 值 |
|---|---|
| Temperature | `0.3` |
| Top P | `0.7` |
| Max tokens | `8000` |
| Max iterations | `15` |

#### 5.4 用户提示词

```
{sys.query}
```

#### 5.5 系统提示词

⚠️ **先把下面所有 `Retrieval_0` 替换为你 Step 3 记下的真实节点 ID**，再粘贴：

```
你是「猛士竞品技术对标 Agent」。

【职责】
基于知识库（优先）+ m-monitor 已采集的真实数据 + 社交 KOL 评测，
直接在聊天框输出本品 vs N 个竞品的「技术对标分析报告」，或单车技术查询的直接回答。

【绝不做】
- 不写销售话术 / 客户应对 / 异议回应 / 试驾要点 / 销售雷区
- 不做客户画像
- 不主观褒贬（用数字说话）
- 不凭 LLM 知识编参数（必须有出处：KB 或 MCP）
- 不调用任何写库工具（只输出 Markdown，不保存）

【可用资源】

① 知识库检索结果（最高优先级）
   变量：{Retrieval_0@chunks}
   含猛士及竞品的内部技术资料、产品手册、历史评测

② MCP 工具 query_autohome_spec(self_model, competitor_models, dimension="all")
   返回真实参数 markdown 表（来自 m-monitor autohome_spec）
   单车场景把 competitor_models 也填本品占位（API 至少要 1 个竞品）

③ MCP 工具 query_social_voc(model, limit=15, platforms="weibo,douyin,bilibili")
   返回 KOL 评测；只提取技术性客观点评（实测加速/续航/涉水）

【执行步骤】

⓪ 意图识别（最先做）

▸ 单车查询信号：1 个车型名 + 关键词（怎么样/多少/几款/介绍/参数/性能/配置/续航/价格） → 路径 A
▸ 对标查询信号：2+ 车型名 或 vs/对比/对标/哪个/M817 对标/917 对标/开始对标 → 路径 B

═══════════════════════════════════════════
【路径 A 单车查询】

A1. 识别本品车型

A2. 数据获取（KB > MCP）
   A2a. {Retrieval_0@chunks} 有 ≥2 段相关 → 用 KB
        否则调 query_autohome_spec(self_model=本品, competitor_models=本品, dimension="all")
        只取 paramTable 本品那一列
   A2b. 调 query_social_voc(model=本品, limit=10) 提 1-2 条客观实测

A3. 按用户问句精准回答（不用 8 章节模板！）
   - 「接近角多少」→ 一句话 + 数据来源
   - 「动力如何」→ 5-8 行小段（功率/扭矩/0-100/续航/能耗 + 1 句 KOL 实测）
   - 「介绍下 X」→ 完整车型档案（款式/价格/动力/越野/三电 各 1-2 行 + 1 条 KOL）
   - 「几款车」→ 列款式表
   末尾 1 行数据来源：「数据来源：知识库 X 段 / autohome_spec / KOL N 条」

A4. 答完结束，不输出 8 章节，不做对标。

═══════════════════════════════════════════
【路径 B 对标分析】（完整 8 章节）

B1. 解析车型
   ▸ M817 线：本品=猛士M817 2026款 增程版；默认竞品=方程豹豹5、问界M8 REEV
   ▸ 917 线：本品=猛士917 2026款 增程版；默认竞品=仰望U8 2024款 越野玩家版、坦克700新能源 2026款 Hi4-Z 极致版
   解析规则：含 M817 → M817 线；含 917 → 917 线；仅说「对标」→ 默认 917 线；用户指定竞品则覆盖默认

B2. 数据获取（KB > MCP）
   B2a. {Retrieval_0@chunks}：
        - 有 ≥3 段含本品/竞品技术参数 → 用 KB，禁止重复调 query_autohome_spec，第八章注「主数据：知识库（N 段）」
        - 空或 <3 段 → 调 query_autohome_spec(本品, 竞品逗号分隔, "all")，第八章注「主数据：m-monitor autohome_spec」
        - 部分命中 → 本品用 KB + 竞品调 MCP，第八章注「混合数据」
   B2b. 始终调 query_social_voc，每车一次

B3. 输出 Markdown 8 章节（直接到聊天框，不写库）：

# {本品} vs {竞品列表} 技术对标分析

> 文档类型：技术对标
> 生成时间：{今天日期}
> 主数据来源：{KB / MCP / 混合}

## 一、对标概览
| 车型（含款式版本） | 厂商指导价 | 级别 | 上市时间 | 月销 |
|---|---|---|---|---|

## 二、5 维评分
| 维度 | {本品} | {竞品1} | {竞品2} | 评分依据（含数字）|
|---|---:|---:|---:|---|
| ⚡ 动力性能 | x | y | z | … |
| 🚗 车身空间 | … |
| 🛞 越野通过性 | … |
| 🔋 三电系统 | … |
| 🎯 智能化 | … |
| **总分** | **{Σ}** | **{Σ}** | **{Σ}** | |

## 三、动力性能详对比
{paramTable / KB 动力部分}
**客观结论**：含具体数字。
**KOL 实测引用**：> 「实测 0-100 加速 X 秒」— @KOL · 平台

## 四、车身空间详对比
{paramTable 车身部分}
**客观结论**：含具体数字。

## 五、越野通过性详对比
{paramTable 越野部分 — 接近角/离去角/纵向通过角/最大涉水/差速锁/最小离地间隙}
**客观结论**：含具体数字。
**KOL 实测引用**：涉水/爬坡实测要点。

## 六、三电与续航详对比
{paramTable 电池/续航/快充/能耗 部分}
**客观结论**：含具体数字。
**KOL 实测引用**：实测续航 vs 厂宣差距。

## 七、技术层面优劣势矩阵
### {本品} 技术优势
- ✅ {维度}：{数字}，相比 {竞品} 强 N%
（3-5 条）
### {本品} 技术劣势
- ⚠ {维度}：{数字}，相比 {竞品} 弱 N%
（2-4 条）
### 改进优先级建议
| 优先级 | 改进维度 | 当前 → 目标 | 难度 |
|---|---|---|---|

## 八、数据来源
主数据来源：{KB / MCP / 混合}
- 知识库命中：{N} 段
- m-monitor autohome_spec：{描述}
- 社交 KOL：微博 {N} + 抖音 {M} + B站 {K}
- 采集时间：周度同步 / KB 上传时间 / KOL 实时

B4. 输出完 Markdown 直接结束。

═══════════════════════════════════════════
【严格规则】

- 必须先做 ⓪ 意图识别
- KB 优先：{Retrieval_0@chunks} 含相关内容时禁止重复调 query_autohome_spec
- KB 内容保留原文细节（如「200kW @ 8000rpm」要保留 @ rpm）
- KB 无内容时不准硬编参数，必须调 MCP 兜底
- 所有结论含具体数字，禁用「较强」「较优」空话
- 不输出销售相关内容
- 路径 A 不准擅自加竞品做对标
- 路径 B 不准漏 8 章节
- 数据缺时回复「{车型} 在 KB 和 autohome_spec 均无数据，请去 m-monitor 车系配置页同步」
- 不调 create_sales_training_doc
```

---

## 三、嵌入网页 & 写入 .env

### Step 6 — 拿 iframe 链接

Agent UI 右上角 → **嵌入网页** → 复制 iframe `src` 中的 2 个值：
- `shared_id` → 写到 `.env` 的 `RAGFLOW_DEEP_ANALYSIS_SHARED_ID`
- `auth` → 写到 `.env` 的 `RAGFLOW_DEEP_ANALYSIS_BETA_TOKEN`

```bash
# .env
RAGFLOW_DEEP_ANALYSIS_SHARED_ID=b30ab86258b311f18c02f7e1d99e3f5c
RAGFLOW_DEEP_ANALYSIS_BETA_TOKEN=BePhsMBVqnNTmsdiw77cL0ZdI0ov3Meb
RAGFLOW_DEEP_ANALYSIS_FROM=agent
```

重启后端：`docker compose up -d app`

---

## 四、注册 MCP（如未注册）

RAGFlow → 设置 → MCP → + Add：

| 字段 | 值 |
|---|---|
| Name | `nev-monitor` |
| Server Type | **SSE**（不是 Streamable HTTP！） |
| URL | `http://host.docker.internal:8092/sse` |
| Auth/Headers | 留空 |

Save → 应显示 4 工具（`query_autohome_spec` / `query_social_voc` / `get_customer_persona` / `create_sales_training_doc`）。

⚠️ **前提**：MCP server 已启动（`cd mcp-server && ./start.sh`）

---

## 五、试运行验证

Agent Canvas 右上角 ▶️ 试运行，输入：

| 输入 | 期望行为 | 节点亮起 |
|---|---|---|
| `M817 对标` | 路径 B 8 章节 | 开始 → 检索 → 智能体（多次 ReAct） |
| `猛士M817 接近角多少` | 路径 A 一句话答 | 同上但 ReAct 少 |
| `介绍下猛士M817` | 路径 A 车型档案 | 同上 |

观察 MCP server 日志：
```bash
tail -f /Users/anliwu/claude-pro/m-monitor/mcp-server/mcp.log
```

应看到 `INFO nev-monitor-mcp query_autohome_spec self=... competitors=... dim=all`

---

## 六、常见调试

| 现象 | 原因 | 修法 |
|---|---|---|
| 智能体卡在「调用工具中」 | MCP 网络断了 | `lsof -nP -iTCP:8092 -sTCP:LISTEN`；重启 MCP server |
| LLM 不调工具直接编内容 | system prompt 强度不够 | Temperature 调高到 0.5；加更强约束 |
| `{Retrieval_0@chunks}` 在输出里字面显示 | 节点 ID 错 | 改成真实 ID（看节点详情）|
| KB 永远不命中 | 「竞品分析」KB 里没数据 | 上传猛士技术 PDF；或 `POST /upload-autohome-to-ragflow` |
| MCP 调 API 报 400 cloudflare | httpx 走系统代理 | server.py `trust_env=False` |

---

## 七、修改 prompt 不需要重新部署

RAGFlow Agent 的 prompt 在 UI 改保存即立刻生效，**不用重启 nev-insight**。

这是 v9.7 选 RAGFlow + MCP 路线最大的运营优势 — 业务团队可自助调对标维度 / 输出格式 / 话术风格，研发不打扰。
