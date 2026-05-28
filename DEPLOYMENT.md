# m-monitor 部署手册

> 适用版本：v9.7（含 RAGFlow Agent + MCP + 飞书 OAuth + H5）
> 目标读者：运维 / 开发部署人员
> 阅读时间：30 分钟

## 0. 系统组成

| 服务 | 端口 | 部署方式 | 依赖 |
|---|---|---|---|
| **nev-insight**（Java 后端） | 8090 | Docker | MySQL · Redis · Kafka · 通义千问 API |
| **nev-ui**（Vue 前端 Nginx） | 3080 | Docker | — |
| **MySQL 8.0** | 3306 | Docker | — |
| **Redis** | 6379 | Docker | — |
| **Kafka** | 9092 | Docker | Zookeeper |
| **crawler-service**（Python 爬虫） | 8091 | 本地（Mac 桌面） | Playwright + Chromium |
| **nev-monitor-mcp**（Python MCP） | 8092 | 本地 | Python 3.11+ |
| **RAGFlow**（独立容器） | 80 / 9380 | Docker | 已独立部署 |

---

## 1. 前置依赖

### 1.1 软件版本

| 软件 | 最低版本 | 备注 |
|---|---|---|
| Docker Desktop | 24.x | Mac/Linux |
| Java | 11 | 用于本地开发；运行用 Docker 镜像即可 |
| Maven | 3.8.x | 本地构建 |
| Node.js | 18.x | 前端构建 |
| Python | 3.11+ | MCP server + 爬虫服务 |
| MySQL | **8.0+** | **必须** — 用窗口函数 ROW_NUMBER() |

### 1.2 外部账号

| 服务 | 申请地址 | 用途 |
|---|---|---|
| 阿里云通义千问 | https://dashscope.aliyun.com | LLM + Vision OCR |
| BochaAI 搜索 | https://api.bocha.cn | 新闻爬取 |
| 飞书机器人 | 群内右上 → 设置 → 群机器人 → 自定义机器人 | 推送日报 |
| 飞书企业自建应用 | https://open.feishu.cn/app | OAuth 登录 H5 |
| RAGFlow | https://github.com/infiniflow/ragflow | 知识库 + Agent 编排 |

---

## 2. 后端 / 数据库部署

### 2.1 克隆 + 配置

```bash
git clone <repo-url> m-monitor
cd m-monitor
cp .env.example .env
vim .env  # 填入 BOCHA / DASHSCOPE / FEISHU 等真实 key
```

### 2.2 启动核心服务

```bash
docker compose up -d                    # 启动 MySQL + Redis + Kafka + app + ui
docker compose logs -f nev-insight      # 观察启动日志，等到 "Started Application in X seconds"
```

启动后自动加载 `nev-admin/src/main/resources/db/schema.sql`（初始 DDL）。

### 2.3 应用增量迁移脚本

按时间顺序跑（在 `nev-admin/src/main/resources/db/`）：

```bash
for sql in \
    migration_platform_tables.sql \
    migration_creator_tables.sql \
    migration_brand_keyword.sql \
    migration_keyword_v2.sql \
    migration_crawler_schedule.sql \
    migration_autohome.sql \
    migration_autohome_v2.sql \
    migration_event_v3.sql \
    migration_official_account.sql \
    migration_sales_authority_v7.sql \
    migration_v8_ocr.sql \
    migration_gasgoo_sales.sql \
    migration_sales_training_doc.sql; do
  echo "Applying $sql..."
  docker exec -i nev-mysql mysql -uroot -pnev123456 \
    --default-character-set=utf8mb4 nev_insight \
    < nev-admin/src/main/resources/db/$sql
done
```

⚠️ **必须加 `--default-character-set=utf8mb4`** 否则中文乱码。

### 2.4 验证后端

```bash
curl http://localhost:8090/api/v1/competitor-report/deep-analysis-url
# 应返回 200 + configured 字段
```

---

## 3. 前端部署

```bash
cd nev-admin-ui
npm install                          # 装依赖
npm run build                        # 输出 dist/
cd ..
docker compose up -d --build ui      # nginx 容器把 dist/ 复制进去
```

⚠️ **不要每次都 build** —— 改前端代码后才需要重新 `npm run build && docker compose up -d --build ui`。

验证：浏览器开 http://localhost:3080 → 应显示 m-monitor 首页。

---

## 4. 爬虫服务部署（本地 Mac，不在 Docker 内）

### 4.1 首次启动

```bash
cd crawler-service
./start-local.sh                     # 自动建 venv + 装依赖 + Playwright Chromium
```

首次约 5-10 分钟。最后看到「Uvicorn running on http://0.0.0.0:8091」即成功。

### 4.2 守护进程模式

```bash
nohup ./keepalive.sh > keepalive.log 2>&1 &
# crash 后 5 秒自动拉起
```

### 4.3 验证

```bash
curl http://localhost:8091/  # 应返回 FastAPI Swagger 入口
```

### 4.4 改 Python 代码后

```bash
pkill -f 'uvicorn server:app'        # keepalive 5 秒后自动拉起新进程
```

---

## 5. MCP server 部署（v9.7 新增）

### 5.1 启动

```bash
cd mcp-server
./start.sh                           # 自动用 python3.11 建 venv + 装 mcp/httpx/pymysql
# 成功显示 "Uvicorn running on http://0.0.0.0:8092"
```

如缺 Python 3.11：`brew install python@3.11`。

### 5.2 守护

```bash
# 简单 nohup
nohup ./start.sh > mcp.log 2>&1 &

# 或者用 launchd / systemd 持久化（生产环境）
```

### 5.3 验证

```bash
# 从 RAGFlow 容器内访问
docker exec docker-ragflow-cpu-1 sh -c "curl -sN --max-time 2 -H 'Accept: text/event-stream' 'http://host.docker.internal:8092/sse' | head -3"
# 应输出：event: endpoint
#         data: /messages/?session_id=...
```

---

## 6. RAGFlow 集成（v9.7 新增）

### 6.1 前提

RAGFlow 已独立部署（参考 https://github.com/infiniflow/ragflow），跑在 host 80 端口。

### 6.2 注册 MCP server

打开 http://localhost/ → 登录 → 设置 → MCP → 添加：

| 字段 | 值 |
|---|---|
| Name | `nev-monitor` |
| Server Type | **SSE** （不是 Streamable HTTP） |
| URL | `http://host.docker.internal:8092/sse` |
| Headers / Auth | 留空 |

Save → 应显示 **4 tools**。

### 6.3 创建 Agent

RAGFlow UI → Agent → + Create agent → Blank → 命名「猛士竞品技术对标」

Canvas 结构：
```
[开始] → [检索] → [智能体]
```

各节点配置见 `docs/RAGFlow-深度对标-Agent-配置指南.md`。

### 6.4 拿 iframe URL

Agent UI 右上角 → 嵌入网页 → 复制 src，提取 `shared_id` + `auth` 值。

写入 `.env`：
```bash
RAGFLOW_DEEP_ANALYSIS_SHARED_ID=<agent canvas id>
RAGFLOW_DEEP_ANALYSIS_BETA_TOKEN=<beta auth>
RAGFLOW_DEEP_ANALYSIS_FROM=agent
```

重启 app：`docker compose up -d app`。

### 6.5 应用 mobile patch（必跑）

```bash
mcp-server/apply-ragflow-mobile-patch.sh
```

⚠️ **每次 RAGFlow 容器重建后都要重跑**。

---

## 7. 飞书 OAuth 配置（H5 强制登录）

### 7.1 飞书后台创建应用

1. https://open.feishu.cn/app → 创建企业自建应用「猛士竞品对标」
2. **凭证与基础信息**页拿到 `App ID` + `App Secret`
3. **安全设置 → 重定向 URL** 白名单加：
   ```
   http://localhost:3080/h5/benchmark
   # 生产：https://your-domain/h5/benchmark
   ```
4. **权限管理**勾选：
   - `获取用户的基本信息` (`contact:user.id`)
5. **版本管理** → 创建版本 → 发布（内部测试模式不需提审）
6. **可用范围管理** → 加测试人员

### 7.2 写入 .env

```bash
FEISHU_APP_ID=cli_xxxxxxxx
FEISHU_APP_SECRET=xxxxxxxx
FEISHU_OAUTH_REDIRECT=http://localhost:3080/h5/benchmark
FEISHU_REQUIRE_LOGIN=true
```

### 7.3 重启 + 验证

```bash
docker compose up -d app
curl http://localhost:8090/api/v1/feishu-auth/config
# 应返回 configured:true, requireLogin:true
```

浏览器打开 `http://localhost:3080/h5/benchmark` → 应显示「飞书登录」按钮。

---

## 8. 端到端验证

```bash
# 1. 触发竞品日报生成 + 推送
curl -X POST 'http://localhost:8090/api/v1/competitor-report/generate?push=true'

# 2. 飞书应收到卡片，点底部「🤖 智能竞品对标」按钮
# 3. 浏览器跳 http://localhost:3080/h5/benchmark
# 4. 走飞书 OAuth → 头像出现在右上角
# 5. iframe 加载 RAGFlow Agent
# 6. 输入 "M817 对标" → Agent 调 MCP → 输出 8 章节 Markdown
```

观察各组件日志：
```bash
docker logs -f nev-insight              # 后端
tail -f mcp-server/mcp.log              # MCP 工具调用
docker logs -f docker-ragflow-cpu-1     # RAGFlow
```

---

## 9. 常见问题

### Q1：RAGFlow 测 MCP 报「102 Connection failed」

A：RAGFlow 把所有连接异常都包装成这句，**不一定是 auth 问题**。看真实错误：
```bash
docker logs docker-ragflow-cpu-1 --since 5m 2>&1 | grep -B2 -A20 'Exception\|Traceback' | tail -40
```

常见原因：
- 用了 Streamable HTTP transport → 改用 SSE
- URL 末尾带 `/` 触发 307 redirect → 去掉
- MCP server 没起 → `lsof -nP -iTCP:8092 -sTCP:LISTEN`

### Q2：MCP 调 m-monitor API 返回 400 "HTTPS port"

A：httpx 走了系统代理（Clash / WARP / 企业代理）。确认 `server.py` 里所有 `httpx.Client(...)` 都有 `trust_env=False`。

### Q3：飞书 OAuth 回调失败

A：检查：
- 飞书后台「重定向 URL」白名单**完全匹配** `FEISHU_OAUTH_REDIRECT`（含协议、端口）
- App ID/Secret 没写错
- 应用是否已发布 / 测试人员是否在可用范围

### Q4：移动端打开 H5 排版不对

A：跑一次 `mcp-server/apply-ragflow-mobile-patch.sh`。若仍不对，验证 mobile-patch.css 有没有加载：
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/mobile-patch.css
# 应 200
```

### Q5：autohome_spec 表查不到某车型

A：series_id 可能错。去 `https://www.autohome.com.cn/grade/carhtml/{首字母}.html` 找真 ID，更新 `autohome_series_config` 表，跑 `POST /api/v1/autohome/sync/{seriesId}` 重抓。

---

## 10. 升级指南

### 10.1 应用代码更新

```bash
git pull
mvn package -DskipTests -pl nev-admin -am -q   # 编译后端
cd nev-admin-ui && npm run build && cd ..       # 编译前端
docker compose up -d --build app ui             # 重启容器
```

### 10.2 数据库迁移

新版本会在 `nev-admin/src/main/resources/db/` 加新的 `migration_v*.sql`。按时间顺序跑。

### 10.3 RAGFlow 升级后

```bash
mcp-server/apply-ragflow-mobile-patch.sh         # 重新注入 mobile CSS
# 验证 MCP 仍可连接，Agent 工具仍挂载
```

### 10.4 MCP server 更新

```bash
# 杀掉旧进程
pkill -f 'python.*server\.py'
# 重启
cd mcp-server && ./start.sh
```

---

## 11. 生产环境注意事项

| 项 | 开发 | 生产 |
|---|---|---|
| HTTPS | localhost http 即可 | 必须 HTTPS（飞书 OAuth 要求） |
| 域名 | localhost:3080 | 公网域名（飞书重定向需公网可达） |
| MCP server | 跑在 Mac 桌面 | 部署到服务器（systemd / supervisor） |
| 爬虫 | Mac 桌面（弹 Chromium 扫码） | 同上（首次扫码后 cookie 文件可复用） |
| `.env` 权限 | 777 | 600（含密钥） |
| 数据库 | localhost root | 独立账号 + 最小权限 |
| 飞书 App | 内部测试模式 | 提审正式发布 |

---

## 12. 备份

```bash
# DB 备份
docker exec nev-mysql mysqldump -uroot -pnev123456 \
  --default-character-set=utf8mb4 nev_insight \
  | gzip > backup-$(date +%Y%m%d).sql.gz

# 全栈备份（含 docker volumes）
docker run --rm -v nev_mysql_data:/data -v $(pwd):/backup \
  alpine tar czf /backup/mysql-volume-$(date +%Y%m%d).tar.gz /data
```

---

## 13. 联系与支持

- **代码仓库**：see git remote
- **架构问题**：先读 `CLAUDE.md`
- **业务背景**：`docs/竞品动态-业务方案.md`
- **Agent 配置**：`docs/RAGFlow-深度对标-Agent-配置指南.md`
- **变更历史**：`CHANGELOG.md`
