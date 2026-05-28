"""
nev-monitor-mcp — m-monitor REST + DB 暴露给 RAGFlow Agent 用的 MCP server。

工具清单（Tools）：
  query_autohome_spec(self_model, competitor_models, dimension)
      → 调 m-monitor /api/v1/benchmark/data 返回某维度参数对比 markdown 表格

  query_social_voc(model, limit, platforms)
      → SQL 查 weibo_note / douyin_aweme / bilibili_video，返回 VOC 列表

  get_customer_persona(model_tier)
      → 高端越野 / 主流 SUV 等人群画像模板

  create_sales_training_doc(title, target_model, competitor_models,
                            content_md, summary, sources)
      → 调 m-monitor /api/v1/sales-training-doc 创建文档，返回 {id, viewUrl}

启动：
  uv run server.py            # streamable HTTP at http://0.0.0.0:8092/sse
  python -m server            # 同上（如已 pip install）

配置：
  M_MONITOR_API_BASE  默认 http://host.docker.internal:8090
  M_MONITOR_FE_BASE   默认 http://localhost:3080  (返回 viewUrl 用)
  DB_HOST/PORT/USER/PASSWORD/NAME
"""
from __future__ import annotations

import json
import logging
import os
from typing import Any

import httpx
import pymysql
from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("nev-monitor-mcp")

# ─── 配置 ─────────────────────────────────────────────────────────────────
API_BASE = os.getenv("M_MONITOR_API_BASE", "http://host.docker.internal:8090")
FE_BASE = os.getenv("M_MONITOR_FE_BASE", "http://localhost:3080")

DB = dict(
    host=os.getenv("DB_HOST", "127.0.0.1"),
    port=int(os.getenv("DB_PORT", "3306")),
    user=os.getenv("DB_USER", "root"),
    password=os.getenv("DB_PASSWORD", "nev123456"),
    database=os.getenv("DB_NAME", "nev_insight"),
    charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor,
)

# 关闭 FastMCP 默认的 DNS rebinding 保护 — 允许容器经 host.docker.internal 访问
_security = TransportSecuritySettings(enable_dns_rebinding_protection=False)

mcp = FastMCP("nev-monitor", transport_security=_security, instructions="""
m-monitor 项目数据访问 MCP — 给 RAGFlow Agent 调用。

核心场景：销售培训文档生成。Agent 应按下面顺序串：
  1) query_autohome_spec(self_model, competitor_models, "all")
        拉真实参配作为「产品配置速览」
  2) query_social_voc(model, 20)
        拉社交 VOC（B站/抖音/微博 KOL 评测 + 用户评论）
  3) get_customer_persona("luxury_offroad")
        拉客户画像
  4) （可选）外部 chrome-devtools/tavily MCP 补内容
  5) create_sales_training_doc(...)
        把综合后的 markdown 写入 m-monitor，返回 viewUrl 给用户
""")


# ─── 工具 1：拉 m-monitor 真实参配 ──────────────────────────────────────
@mcp.tool()
def query_autohome_spec(
    self_model: str,
    competitor_models: str,
    dimension: str = "all",
) -> str:
    """查 m-monitor autohome_spec 表获取本品 + 竞品的真实参数对比。

    Args:
        self_model:        本品车型全称（如「猛士917」）
        competitor_models: 竞品车型逗号分隔（如「仰望U8,坦克700新能源」）
        dimension:         power | body | offroad | price | sales | all

    Returns:
        JSON 字符串，含 paramTable (markdown 表格)、salesTable、priceEvents 等
    """
    log.info("query_autohome_spec self=%s competitors=%s dim=%s",
             self_model, competitor_models, dimension)
    try:
        with httpx.Client(timeout=60, trust_env=False) as c:
            r = c.get(f"{API_BASE}/api/v1/benchmark/data", params={
                "selfModel": self_model,
                "competitorModels": competitor_models,
                "dimension": dimension,
            })
        if r.status_code != 200:
            return json.dumps({"error": f"HTTP {r.status_code}", "body": r.text[:500]},
                              ensure_ascii=False)
        body = r.json()
        if body.get("code") != 200:
            return json.dumps({"error": body.get("message", "未知错误")},
                              ensure_ascii=False)
        return json.dumps(body.get("data", {}), ensure_ascii=False)
    except Exception as e:
        log.exception("query_autohome_spec failed")
        return json.dumps({"error": str(e)}, ensure_ascii=False)


# ─── 工具 2：拉社交平台 VOC ──────────────────────────────────────────────
@mcp.tool()
def query_social_voc(
    model: str,
    limit: int = 20,
    platforms: str = "weibo,douyin,bilibili",
) -> str:
    """查社交平台真实用户/KOL 内容（VOC）— 提取购买理由 / 吐槽点 / 异议。

    Args:
        model:     车型关键词（如「猛士917」「仰望U8」）
        limit:     每平台最多条数
        platforms: 平台逗号分隔，支持 weibo / douyin / bilibili

    Returns:
        JSON 数组：[{platform, title, content, author, likes, comments, url}, ...]
    """
    log.info("query_social_voc model=%s limit=%d platforms=%s",
             model, limit, platforms)
    results: list[dict[str, Any]] = []
    plat_list = [p.strip() for p in platforms.split(",") if p.strip()]
    try:
        conn = pymysql.connect(**DB)
    except Exception as e:
        return json.dumps({"error": f"DB 连接失败: {e}"}, ensure_ascii=False)

    queries = {
        "weibo": """
            SELECT 'weibo' AS platform, content AS title, content,
                   nickname AS author, liked_count AS likes,
                   comments_count AS comments,
                   note_url AS url, add_ts
            FROM weibo_note
            WHERE content LIKE %s
            ORDER BY (IFNULL(liked_count,0)+IFNULL(comments_count,0)*5) DESC
            LIMIT %s""",
        "douyin": """
            SELECT 'douyin' AS platform, title, `desc` AS content,
                   nickname AS author, liked_count AS likes,
                   comment_count AS comments,
                   aweme_url AS url, add_ts
            FROM douyin_aweme
            WHERE (title LIKE %s OR `desc` LIKE %s)
            ORDER BY (IFNULL(liked_count,0)+IFNULL(comment_count,0)*5) DESC
            LIMIT %s""",
        "bilibili": """
            SELECT 'bilibili' AS platform, title, `desc` AS content,
                   nickname AS author, liked_count AS likes,
                   video_comment AS comments,
                   video_url AS url, add_ts
            FROM bilibili_video
            WHERE (title LIKE %s OR `desc` LIKE %s)
            ORDER BY (IFNULL(liked_count,0)+IFNULL(video_comment,0)*5) DESC
            LIMIT %s""",
    }
    like = f"%{model}%"
    try:
        with conn.cursor() as cur:
            for p in plat_list:
                if p not in queries: continue
                try:
                    if p == "weibo":
                        cur.execute(queries[p], (like, limit))
                    else:
                        cur.execute(queries[p], (like, like, limit))
                    for row in cur.fetchall():
                        row["title"] = (row.get("title") or "")[:200]
                        row["content"] = (row.get("content") or "")[:500]
                        results.append(row)
                except Exception as e:
                    log.warning("query %s failed: %s", p, e)
    finally:
        conn.close()
    return json.dumps(results, ensure_ascii=False, default=str)


# ─── 工具 3：客户画像 ─────────────────────────────────────────────────────
PERSONAS = {
    "luxury_offroad": {
        "name": "高端越野车客户画像",
        "segments": [
            {"name": "身份表达型", "weight": 0.25,
             "needs": ["车型外观霸气有辨识度", "品牌调性匹配身份", "可定制化配置",
                       "限量版/纪念版稀缺感"],
             "pain": ["竞品同质化严重", "缺乏圈层认同感"],
             "budget": "80-150 万"},
            {"name": "户外越野型", "weight": 0.30,
             "needs": ["接近角离去角通过性强", "差速锁全锁", "涉水深度 ≥80cm",
                       "胎压管理 / 360 全景", "原厂改装件支持", "全国服务网点"],
             "pain": ["改装件不通用", "服务半径有限", "纯电续航不够跑长途"],
             "budget": "50-100 万"},
            {"name": "家庭远行型", "weight": 0.20,
             "needs": ["第二第三排空间 + 配置", "增程 / 大电池续航 ≥1000km",
                       "全车静音 / NVH", "副驾零重力", "后排娱乐"],
             "pain": ["三排坐人后备厢小", "增程模式噪音大", "充电桩规划复杂"],
             "budget": "40-80 万"},
            {"name": "技术体验型", "weight": 0.15,
             "needs": ["智驾水平 NOA 高速 / 城区", "智能座舱大屏 / HUD",
                       "OTA 更新频率", "电池充电速度 800V", "对外放电 V2L"],
             "pain": ["技术迭代快担心保值", "智驾在小城市表现差"],
             "budget": "40-80 万"},
            {"name": "价格理性型", "weight": 0.10,
             "needs": ["性价比 / 保值率", "首付/月供方案", "厂家直降",
                       "免息 / 0 首付政策", "免费保养"],
             "pain": ["竞品降价快", "金融政策吸引但要看长期成本"],
             "budget": "30-60 万"},
        ],
        "common_concerns": [
            "电池衰减担忧（3-5 年后续航打折）",
            "服务网点（高端车保养维修难找）",
            "保值率（新势力 vs 传统豪华品牌差距）",
            "OTA 风险（系统升级后体验变差）",
            "金融方案（看似免息实则总价高）",
        ],
        "sales_strategy": [
            "用真实参数对比避免空谈",
            "针对身份表达型客户强调圈层与限量",
            "针对越野型客户强调真实越野场景演示视频",
            "针对家庭型客户强调家用 + 越野双场景兼顾",
            "针对技术型客户强调 OTA 路线图与底层架构",
            "针对价格型客户强调全生命周期 TCO，不要只比首付",
        ],
    },
    # 后续可扩展 family_suv / urban_ev / etc.
}


@mcp.tool()
def get_customer_persona(model_tier: str = "luxury_offroad") -> str:
    """获取客户画像（人群细分 + 需求 + 痛点 + 销售策略）。

    Args:
        model_tier: 当前支持 luxury_offroad（高端越野车）

    Returns:
        JSON 字符串
    """
    log.info("get_customer_persona tier=%s", model_tier)
    p = PERSONAS.get(model_tier)
    if not p:
        return json.dumps({"error": f"未知 model_tier，可用: {list(PERSONAS)}"},
                          ensure_ascii=False)
    return json.dumps(p, ensure_ascii=False)


# ─── 工具 4：创建销售培训文档 ─────────────────────────────────────────────
@mcp.tool()
def create_sales_training_doc(
    title: str,
    target_model: str,
    content_md: str,
    competitor_models: str = "",
    summary: str = "",
    sources: list[dict] | None = None,
) -> str:
    """把综合好的销售培训文档写入 m-monitor 数据库，返回 viewUrl 给用户。

    Args:
        title:             文档标题（如「猛士917 vs 仰望U8/坦克700 销售培训」）
        target_model:      主推本品（如「猛士917」）
        content_md:        完整 Markdown 文档（9 章节）
        competitor_models: 对标竞品（逗号分隔）
        summary:           一句话摘要
        sources:           资料来源列表 [{type,title,url}]

    Returns:
        JSON: {id, title, viewUrl}
    """
    log.info("create_sales_training_doc title=%s target=%s", title, target_model)
    body = {
        "title": title,
        "targetModel": target_model,
        "competitorModels": competitor_models,
        "contentMd": content_md,
        "summary": summary,
        "sourcesJson": json.dumps(sources or [], ensure_ascii=False),
        "generator": "ragflow-agent",
    }
    try:
        with httpx.Client(timeout=30, trust_env=False) as c:
            r = c.post(f"{API_BASE}/api/v1/sales-training-doc", json=body)
        if r.status_code != 200:
            return json.dumps({"error": f"HTTP {r.status_code}", "body": r.text[:500]},
                              ensure_ascii=False)
        rj = r.json()
        if rj.get("code") != 200:
            return json.dumps({"error": rj.get("message")}, ensure_ascii=False)
        data = rj.get("data", {})
        # 把 viewUrl 拼成绝对地址给用户
        if data.get("viewUrl"):
            data["viewUrl"] = FE_BASE + data["viewUrl"]
        return json.dumps(data, ensure_ascii=False)
    except Exception as e:
        log.exception("create_sales_training_doc failed")
        return json.dumps({"error": str(e)}, ensure_ascii=False)


# ─── 启动 ─────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    # 用 streamable HTTP 模式（RAGFlow v0.25 支持）
    # 默认端口 8000，我们改 8092 避开冲突
    import sys
    port = int(os.getenv("MCP_PORT", "8092"))
    log.info("Starting nev-monitor-mcp on port %d", port)
    log.info("API_BASE=%s  FE_BASE=%s  DB_HOST=%s", API_BASE, FE_BASE, DB["host"])
    # FastMCP 自带 streamable HTTP / SSE 两种 transport
    # RAGFlow v0.25 同时支持，streamable-http 是新协议（更宽容），SSE 旧但兼容性广
    transport = os.getenv("MCP_TRANSPORT", "streamable-http")  # streamable-http | sse
    mcp.settings.port = port
    mcp.settings.host = "0.0.0.0"
    log.info("Transport=%s  → RAGFlow URL: http://host.docker.internal:%d/%s",
             transport, port, "mcp/" if transport == "streamable-http" else "sse")
    mcp.run(transport=transport)
