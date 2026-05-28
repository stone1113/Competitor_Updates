# nev-monitor-mcp

> m-monitor 项目数据访问 MCP server — 给 RAGFlow Agent 提供工具调用能力。
> v9.7 新增组件。

---

## 启动

```bash
./start.sh
```

首次自动建 venv + 装依赖（需 Python 3.11+）。
启动后监听 `http://0.0.0.0:8092/sse`（SSE transport）。

RAGFlow 容器经 `http://host.docker.internal:8092/sse` 接入。

---

## 暴露的 4 个工具

| 工具 | 用途 |
|---|---|
| `query_autohome_spec(self_model, competitor_models, dimension="all")` | 查 m-monitor autohome_spec 表，返回真实参数对比 markdown 表 |
| `query_social_voc(model, limit=15, platforms="weibo,douyin,bilibili")` | 查社交平台 KOL 评测内容 |
| `get_customer_persona(model_tier="luxury_offroad")` | 客户画像模板（高端越野） |
| `create_sales_training_doc(...)` | 写入 m-monitor 销售培训文档表（v9.7 默认不挂） |

---

## 配置（环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `M_MONITOR_API_BASE` | `http://localhost:8090` | m-monitor 后端地址 |
| `M_MONITOR_FE_BASE` | `http://localhost:3080` | m-monitor 前端（拼 viewUrl 用） |
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` / `DB_NAME` | 同 m-monitor | 直查社交表用 |
| `MCP_PORT` | `8092` | 监听端口 |
| `MCP_TRANSPORT` | `sse` | sse / streamable-http |

---

## 常见问题

### Q：RAGFlow 测连接报 102 错误
A：先看真实异常：`docker logs docker-ragflow-cpu-1 --since 5m 2>&1 | grep -B2 -A20 Traceback | tail -40`

常见原因：
- 用了 Streamable HTTP transport → 改 SSE
- URL 末尾带 `/` 触发 307 → 去掉
- 端口被占 → `lsof -nP -iTCP:8092` 杀掉占用

### Q：MCP 调 API 报 400 "HTTPS port"
A：httpx 走了系统代理（Clash / WARP）。所有 `httpx.Client(...)` 必须 `trust_env=False`（server.py 已加）。

### Q：FastMCP 报 "Invalid Host header"
A：FastMCP 默认 DNS rebinding protection 拒非 localhost host header。server.py 已用 `TransportSecuritySettings(enable_dns_rebinding_protection=False)` 关闭。

### Q：Python 版本错误
A：MCP SDK 要求 Python ≥3.10。Mac 默认 Python 3.9 不够。
```bash
brew install python@3.11
PYBIN=python3.11 ./start.sh
```

---

## RAGFlow 移动端 CSS Patch

`apply-ragflow-mobile-patch.sh` — 把 `ragflow-mobile-patch.css` 注入 RAGFlow 容器，让 share 页在 mobile (≤768px) 紧凑显示。

**每次 RAGFlow 容器 down && up 重建后需重跑**。

---

## 文件

```
mcp-server/
├── server.py                       FastMCP 主程序（4 工具）
├── start.sh                        启动脚本（自动建 venv + 装依赖）
├── pyproject.toml                  依赖清单
├── ragflow-mobile-patch.css        RAGFlow 移动端 CSS
├── apply-ragflow-mobile-patch.sh   注入 RAGFlow 容器
└── README.md                       本文档
```
