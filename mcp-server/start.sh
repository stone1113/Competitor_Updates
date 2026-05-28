#!/bin/bash
# nev-monitor-mcp 启动脚本 — 本地跑（不在 docker 内）
set -e
cd "$(dirname "$0")"

# 1) 创建 venv（首次）— 强制 Python 3.11（mcp SDK 要求 >=3.10）
PYBIN="${PYBIN:-python3.11}"
if ! command -v "$PYBIN" >/dev/null 2>&1; then
    echo "❌ 找不到 $PYBIN，请先安装：brew install python@3.11"
    exit 1
fi
if [ ! -d .venv ]; then
    echo "🔧 首次启动，创建虚拟环境（$PYBIN）..."
    "$PYBIN" -m venv .venv
fi
source .venv/bin/activate

# 2) 装依赖（首次或 pyproject.toml 改变）
if [ ! -f .venv/.deps-installed ] || [ pyproject.toml -nt .venv/.deps-installed ]; then
    echo "📦 安装依赖..."
    pip install --quiet --upgrade pip
    pip install --quiet "mcp[cli]>=1.2.0" httpx pymysql uvicorn starlette
    touch .venv/.deps-installed
fi

# 3) 加载 .env（如有）
if [ -f ../.env ]; then
    set -a
    source <(grep -E '^(DB_|M_MONITOR_|MCP_)' ../.env 2>/dev/null || true)
    set +a
fi

# 默认 m-monitor 跑在宿主 8090
export M_MONITOR_API_BASE="${M_MONITOR_API_BASE:-http://localhost:8090}"
export M_MONITOR_FE_BASE="${M_MONITOR_FE_BASE:-http://localhost:3080}"
export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3306}"
export DB_USER="${DB_USER:-root}"
export DB_PASSWORD="${DB_PASSWORD:-nev123456}"
export DB_NAME="${DB_NAME:-nev_insight}"
export MCP_PORT="${MCP_PORT:-8092}"

echo "🚀 启动 nev-monitor-mcp on :$MCP_PORT"
echo "   API_BASE=$M_MONITOR_API_BASE"
echo "   FE_BASE=$M_MONITOR_FE_BASE"
echo "   DB_HOST=$DB_HOST"
echo ""
echo "RAGFlow 配置时 MCP URL 填：http://host.docker.internal:$MCP_PORT/sse"
echo "─────────────────────────────────────────────────────────────"
exec python server.py
