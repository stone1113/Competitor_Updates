# RAGFlow Agent 「深度对标」配置指南

> v9.6 — 业务方在 RAGFlow UI 配置 Agent workflow，Java 通过 HTTP API 调用，可视化改 prompt 不需要重新部署后端。

---

## 一、整体架构

```
浏览器 → Java /api/v1/chat/start  →  RAGFlow Agent /completions
                                            ↓
                              ┌─────────────────────────┐
                              │ Begin (输入 query)       │
                              │   ↓                       │
                              │ [Invoke 节点 × 5 并行]    │
                              │   → GET Java 数据 API    │
                              │     /api/v1/benchmark/data│
                              │   ↓                       │
                              │ [Generate × 5 分析师]    │
                              │   ↓                       │
                              │ [Generate Synthesizer]   │
                              │   → 输出 Markdown 报告    │
                              │   ↓                       │
                              │ Message (回客户端)        │
                              └─────────────────────────┘
                                            ↓
                                    Java 写入 DB
                                            ↓
                                  前端展示 + 推飞书
```

---

## 二、Step-by-Step 在 RAGFlow UI 配置

### 1. 进入 Agent 创建页

打开 `http://localhost/` → 登录 RAGFlow → 左侧菜单「Agent」→ 点「Create agent」→ 选「Blank canvas」

### 2. 命名 & 描述

- **Title**：`猛士竞品深度对标`
- **Description**：`5 分析师并行 + 综合判断 + 图文 Markdown 报告`

### 3. 配置节点（拖拽到画布）

#### 3.1 Begin 节点（已默认存在）

- **Query 变量**：`question`（用户传入的对标诉求，如「比较 M817 vs 豹5 重点看电池」）
- 添加自定义变量：
  - `self_model`（必填，本品车型名，如 `猛士M817`）
  - `competitor_models`（必填，竞品名逗号分隔，如 `方程豹豹5,问界M8 REEV`）

#### 3.2 Invoke 节点（数据采集，命名为 `DataCollector`）

- **位置**：Begin 后第一个
- **URL**：`http://host.docker.internal:8090/api/v1/benchmark/data`
- **Method**：GET
- **Headers**：（无需 token，内网直连）
- **Query params**：
  - `selfModel` = `{Begin@self_model}`
  - `competitorModels` = `{Begin@competitor_models}`
  - `dimension` = `all`
- **输出变量**：`benchmark_data`（含全部 5 维度数据 + 销量 + 价格事件）

#### 3.3 Generate × 5（5 个分析师，并行）

为每个维度建一个 Generate 节点：

##### ⚡ PowerAnalyst
- **Model**：qwen-max（需 RAGFlow 已配置通义千问；在 Settings → Model providers 加 `DashScope`）
- **Temperature**：0.3
- **System prompt**：

```
你是「动力性能分析师」，专门评估新能源汽车的动力总成。

【任务】
1. 仔细对比表格里 5 项动力参数（最大功率/扭矩/加速/续航/能耗）
2. 给每个车型 0-10 分打分（10 = 同类最佳）
3. 选出该维度胜者
4. 输出 3-5 句关键发现，每句必须含具体数字

【严格 JSON 输出（无 markdown 包裹）】
{
  "dimension": "动力",
  "winner": "<车型名>",
  "scores": {"<车型名>": <0-10>, ...},
  "key_findings": ["<含数字的对比>", ...]
}
```

- **User prompt**：

```
【对标场景】本品 {Begin@self_model} vs 竞品 {Begin@competitor_models}

【动力参数表】
{DataCollector@benchmark_data.data.dimensions.power}

按 system 指令分析返回 JSON。
```

##### 🚗 BodyAnalyst / 🛞 OffroadAnalyst / 💰 PriceAnalyst / 📈 SalesAnalyst

复制 PowerAnalyst，修改：
- 名字（BodyAnalyst / OffroadAnalyst / ...）
- system prompt 里「动力性能 / 动力参数」改成对应维度
- User prompt 的 `.dimensions.power` 改成 `.dimensions.body` / `.offroad` / `.price` / 销量改 `.sales`
- PriceAnalyst 额外加 `{DataCollector@benchmark_data.data.dimensions.priceEvents}`
- SalesAnalyst 用 `.sales` + 写「胜者按近 3 月销量+趋势综合判断」

**并行连接**：5 个 Generate 都从 DataCollector 出来（拖 5 条线）。

#### 3.4 Generate 节点（Synthesizer，综合分析师）

- **Model**：qwen-max
- **Temperature**：0.5
- **System prompt**：

```
你是首席汽车产品分析师，综合 5 位分析师的 JSON 结果，输出图文并茂的 Markdown 深度对标报告。

【严格 Markdown 格式（无外层代码块包裹）】

# {本品} vs {N} 款竞品 深度对标报告
> 报告时间：{today}

## 一、执行摘要
{200 字内综合论断}

## 二、综合评分（含雷达图）

```chart
{"type":"radar","data":{"labels":["动力","车身","越野","价格","销量"],"datasets":[
{"label":"{本品}","data":[s1,s2,s3,s4,s5]},
{"label":"{竞品1}","data":[s1,s2,s3,s4,s5]}
]}}
```

## 三、维度对比
### ⚡ 动力性能
- **胜者**：{winner}
- **评分**：本品 X/10  竞品 Y/10
- **关键发现**：（保留分析师 findings 原文）

### 🚗 车身空间
### 🛞 越野通过性
### 💰 价格价值
### 📈 销量市场

## 四、综合胜负表
| 车型 | 动力 | 车身 | 越野 | 价格 | 销量 | 总分 |
|---|---:|---:|---:|---:|---:|---:|

## 五、战术建议（针对本品）
1. （含维度 + 数字 + 可操作动作）
2-5. ...

## 六、风险提示
- ...

【约束】
- 雷达图必须输出（5 维度）
- 销量趋势 chart：仅当 SALES 分析师含具体月份数字时输出
- 不要捏造，只用 5 分析师给的数据
- 不要外层 ```markdown 围栏
```

- **User prompt**：

```
【对标场景】本品 {Begin@self_model} vs 竞品 {Begin@competitor_models}

【5 位分析师的 JSON】

--- ⚡ 动力 ---
{PowerAnalyst@output}

--- 🚗 车身 ---
{BodyAnalyst@output}

--- 🛞 越野 ---
{OffroadAnalyst@output}

--- 💰 价格 ---
{PriceAnalyst@output}

--- 📈 销量 ---
{SalesAnalyst@output}

按 system 指令输出 Markdown 报告。
```

#### 3.5 Message 节点（输出）

把 Synthesizer 的输出直接送 Message 节点（默认终端节点）。

---

## 三、保存 + 拿 Agent ID

1. 点右上角 **Save**
2. 点 **Embed** 或 URL 栏看 Agent ID（如 `ebd22e92534411f1a4e589fa16c565c7`）
3. 复制 Agent ID

---

## 四、配置 Java 后端连通

在 `.env`（项目根目录）加：

```bash
NEVINSIGHT_RAGFLOW_DEEP_BENCHMARK_AGENT_ID=<复制的 Agent ID>
# 可选：单 LLM 兜底（当 Agent 故障）
NEVINSIGHT_RAGFLOW_DEEP_BENCHMARK_CHAT_ID=<RAGFlow Chat Assistant ID>
NEVINSIGHT_RAGFLOW_FOLLOWUP_CHAT_ID=<追问用 Chat ID>
```

或在 `application.yml` 加：

```yaml
nevinsight:
  ragflow:
    deep-benchmark-agent-id: ${NEVINSIGHT_RAGFLOW_DEEP_BENCHMARK_AGENT_ID:}
    deep-benchmark-chat-id: ${NEVINSIGHT_RAGFLOW_DEEP_BENCHMARK_CHAT_ID:}
    followup-chat-id: ${NEVINSIGHT_RAGFLOW_FOLLOWUP_CHAT_ID:}
```

重启后端：`docker compose up -d --build app`

---

## 五、验证

```bash
# 触发一次对标
curl -X POST "http://localhost:8090/api/v1/chat/start?selfModel=猛士M817&competitorModels=方程豹豹5,坦克400" \
  -H "X-User-Token: test-user"

# 看 assistant message 的 metadata.engine 字段
# - "ragflow-agent" → 走了 Agent ✅
# - "ragflow-chat"  → 走了单 LLM 兜底
# - "java"          → Agent / Chat 都没配，回退 Java
```

---

## 六、业务方日常操作

| 需求 | 操作 |
|---|---|
| 调整动力分析师 prompt | RAGFlow UI → PowerAnalyst → System prompt → 改 → Save |
| 加新分析师（如智驾） | UI 加一个 Generate 节点，从 DataCollector 拉新维度 |
| 切 LLM 模型（qwen-max → qwen-vl-max） | Generate 节点 → Model dropdown |
| 让分析师查 KB | Generate 节点前加 Retrieval 节点（绑定「竞品分析」KB）|

所有改动**立即生效**，不需要重启 Java 后端。

---

## 七、Fallback 策略

| 场景 | 行为 |
|---|---|
| Agent 已配置 + 调用成功 | 走 Agent，metadata.engine=`ragflow-agent` |
| Agent 调用失败 OR 未配置 + Chat 已配置 | 走 Chat Assistant，metadata.engine=`ragflow-chat` |
| Agent + Chat 都未配置 OR 都失败 | Java 5-analyst 编排兜底，metadata.engine=`java` |

业务方可以**渐进式切换**：先配 Chat 简化版上线 → 再升级 Agent 复杂版 → 最终关闭 Java fallback。

---

## 八、常见问题

**Q1：Invoke 节点拿不到 Java 数据？**
- Java 在 docker 内的话，URL 用 `http://host.docker.internal:8090`（已默认）
- 不在 docker 内用 `http://localhost:8090`

**Q2：5 分析师串行而不是并行？**
- RAGFlow v0.23+ 才支持并行 LLM；UI 拖 5 条线到 5 个 Generate 节点都从同一前置出（不是串联）

**Q3：响应太慢？**
- Agent 模式比 Java 慢 30-50%（多一层 HTTP）；改 qwen-turbo 提速但降质量
- 加 Retrieval 节点能让分析师更准但更慢
