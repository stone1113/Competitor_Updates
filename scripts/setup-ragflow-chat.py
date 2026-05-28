#!/usr/bin/env python3
"""
一键创建 RAGFlow Chat Assistant「猛士竞品深度对标」+ 绑定 KB + 输出 chat_id。

用法：
    python3 scripts/setup-ragflow-chat.py

前置：
    - RAGFlow 已启动（docker ps | grep ragflow）
    - .env 含 RAGFLOW_API_KEY / RAGFLOW_KB_ID
"""

import json
import os
import sys
import urllib.request
import urllib.parse
import urllib.error
from pathlib import Path


def load_env(path):
    out = {}
    if not path.exists():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        out[k.strip()] = v.strip().strip('"').strip("'")
    return out


def http_request(method, url, headers=None, body=None, timeout=30):
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    # 跳过代理（避免被 cloudflare/clash 拦截）
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"code": e.code, "message": str(e)}
    except Exception as e:
        return {"code": -1, "message": str(e)}


SYSTEM_PROMPT = """你是猛士汽车的「首席竞品分析师」，专门做新能源汽车深度对标分析。

【任务】
用户会问类似「分析 X 和 Y、Z 的对比」「对比 X 销量」之类问题。你的工作：
1. 从知识库（已上传汽车之家车型参数 xlsx + 客户人群画像）找相关数据
2. 必要时建议用户调用后端 API 拉实时数据：
   GET http://host.docker.internal:8090/api/v1/benchmark/data?selfModel={本品}&competitorModels={竞品1,竞品2}&dimension=power|body|offroad|price|sales|all
3. 用 Markdown 输出图文并茂报告

【输出格式（严格 Markdown，无外层代码围栏）】

# {本品} vs {N} 款竞品 深度对标报告
> 报告时间：{今日}

## 一、执行摘要（200 字内）

## 二、综合评分（含雷达图）

```chart
{"type":"radar","data":{"labels":["动力","车身","越野","价格","销量"],"datasets":[
{"label":"{本品}","data":[s1,s2,s3,s4,s5]},
{"label":"{竞品1}","data":[s1,s2,s3,s4,s5]}
]}}
```

## 三、维度对比
### ⚡ 动力性能
- **胜者**：xxx
- **评分**：本品 X/10  竞品 Y/10
- **关键发现**：（含具体数字）

### 🚗 车身空间
### 🛞 越野通过性
### 💰 价格价值
### 📈 销量市场

## 四、近 3 月销量趋势（仅当数据齐时输出）

```chart
{"type":"line","data":{"labels":["2/2026","3/2026","4/2026"],"datasets":[
{"label":"猛士M817","data":[800,850,866]}
]}}
```

## 五、综合胜负表

| 车型 | 动力 | 车身 | 越野 | 价格 | 销量 | 总分 |
|---|---:|---:|---:|---:|---:|---:|

## 六、战术建议（针对本品，3-5 条可操作）

## 七、风险提示

【约束】
- 不要捏造，只用知识库 / Java API 给的数据
- 雷达图必须输出（5 维度对比）
- chart 代码块必须是合法 JSON
- 战术建议必须可操作（含数字 + 动作）
- 多轮追问时保留上下文，回答可简短

知识库内容：
{knowledge}
"""


def main():
    project_root = Path(__file__).resolve().parent.parent
    env = load_env(project_root / ".env")

    api_key = env.get("RAGFLOW_API_KEY")
    kb_id = env.get("RAGFLOW_KB_ID")
    base_url = env.get("RAGFLOW_BASE_URL", "http://localhost:9380")
    # 把 host.docker.internal 换成 localhost
    base_url = base_url.replace("host.docker.internal", "localhost")

    if not api_key or not kb_id:
        print("❌ .env 缺 RAGFLOW_API_KEY 或 RAGFLOW_KB_ID", file=sys.stderr)
        sys.exit(1)

    print(f"🔧 RAGFlow base: {base_url}  kb: {kb_id[:8]}…")

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    chat_name = "猛士竞品深度对标"

    # Step 1: 检查重名（拉全部列表自己过滤名字）
    print(f"\n📋 检查同名 Chat Assistant...")
    list_resp = http_request("GET", f"{base_url}/api/v1/chats?page=1&page_size=100", headers=headers)
    existing_id = None
    for c in list_resp.get("data", {}).get("chats", []):
        if c.get("name") == chat_name:
            existing_id = c.get("id")
            print(f"   found existing id={existing_id}")
            break

    # 完整 prompt + 配置（用于 PUT 更新）
    full_body = {
        "name": chat_name,
        "description": "5 维深度对标 · 图文 Markdown · 多轮追问",
        "dataset_ids": [kb_id],
        "llm_id": "qwen-max@Tongyi-Qianwen",
        "similarity_threshold": 0.2,
        "vector_similarity_weight": 0.3,
        "top_n": 8,
        "top_k": 1024,
        "prompt_config": {
            "system": SYSTEM_PROMPT,
            "prologue": (
                "你好！我是猛士竞品分析师 🤖\n"
                "告诉我「分析 X vs Y, Z」，我会给你含 5 维度（动力/车身/越野/价格/销量）的深度对标报告（含图表）。"
            ),
            "parameters": [{"key": "knowledge", "optional": False}],
            "empty_response": "",
            "quote": True,
            "keyword": False,
            "tts": False,
            "refine_multiturn": False,
            "reasoning": False,
            "use_kg": False,
            "toc_enhance": False,
        },
    }

    # Step A: 不存在 → 先用最小 body POST 创建
    if not existing_id:
        print("✨ 创建新 Chat Assistant（最小 body）...")
        minimal_body = {
            "name": chat_name,
            "description": full_body["description"],
            "dataset_ids": [kb_id],
            "llm_id": "qwen-max@Tongyi-Qianwen",
            "prompt_config": {
                "system": "init",
                "prologue": "init",
                "parameters": [{"key": "knowledge", "optional": False}],
                "empty_response": "", "quote": True, "keyword": False,
                "tts": False, "refine_multiturn": False, "reasoning": False,
                "use_kg": False, "toc_enhance": False,
            },
        }
        resp = http_request("POST", f"{base_url}/api/v1/chats", headers=headers, body=minimal_body)
        code = resp.get("code")
        if code != 0:
            print(f"❌ POST 失败: code={code} message={resp.get('message')}", file=sys.stderr)
            print(json.dumps(resp, ensure_ascii=False, indent=2)[:600], file=sys.stderr)
            sys.exit(1)
        existing_id = resp.get("data", {}).get("id")
        print(f"   created id={existing_id}")

    # Step B: PUT 更新完整 prompt + 配置
    print(f"📝 PUT 更新 prompt + 完整配置...")
    resp = http_request("PUT", f"{base_url}/api/v1/chats/{existing_id}", headers=headers, body=full_body)
    code = resp.get("code")
    if code != 0:
        print(f"❌ PUT 失败: code={code} message={resp.get('message')}", file=sys.stderr)
        print(json.dumps(resp, ensure_ascii=False, indent=2)[:600], file=sys.stderr)
        sys.exit(1)

    chat_id = existing_id

    print()
    print("✅ 完成！")
    print()
    print(f"  名称   : {chat_name}")
    print(f"  Chat ID: {chat_id}")
    print(f"  KB ID  : {kb_id}")
    print(f"  LLM    : qwen-max@Tongyi-Qianwen")
    print()
    print("📝 在 .env 加这 2 行（覆盖已有）：")
    print()
    print(f"  NEVINSIGHT_RAGFLOW_DEEP_BENCHMARK_CHAT_ID={chat_id}")
    print(f"  NEVINSIGHT_RAGFLOW_FOLLOWUP_CHAT_ID={chat_id}")
    print()
    print("然后重启后端：")
    print("  docker compose up -d --build app")
    print()
    ui_base = env.get("RAGFLOW_UI_BASE_URL", "http://localhost")
    print(f"💬 RAGFlow UI ({ui_base}/) → Chat 标签可见「{chat_name}」")
    print("   业务方可在此改 prompt / 切 LLM / 调 KB，**无需重启 Java 后端**")


if __name__ == "__main__":
    main()
