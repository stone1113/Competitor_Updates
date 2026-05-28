#!/usr/bin/env bash
# 本地启动 crawler 服务（方案 B：不在 Docker 内跑）
#
# 用法：
#   cd crawler-service && ./start-local.sh
#
# 首次运行会：
#   1. 创建 .venv 虚拟环境
#   2. 安装 Python 依赖（mediacrawler/requirements.txt + 本目录 requirements.txt）
#   3. 安装 Playwright 的 Chromium
#   4. 启动 FastAPI on http://0.0.0.0:8091
#
# 之后再次运行只会启动服务（venv 已存在）
#
# 前置：
#   - Python 3.11+（mediacrawler 要求）
#   - Node.js（pyexecjs 跑抖音/B站签名 JS）：brew install node
#   - nev-mysql 容器在跑（提供 3306 端口）

set -euo pipefail

cd "$(dirname "$0")"

# 1. venv （mediacrawler 需要 Python 3.11+）
PYTHON_BIN=${PYTHON_BIN:-python3.11}
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "[start-local] 找不到 $PYTHON_BIN；请 brew install python@3.11 或设 PYTHON_BIN=path"
  exit 1
fi
if [ ! -d .venv ]; then
  echo "[start-local] 创建 venv ($PYTHON_BIN)..."
  "$PYTHON_BIN" -m venv .venv
fi
# shellcheck source=/dev/null
source .venv/bin/activate

# 2. install deps if missing
if ! python -c "import fastapi, playwright, asyncmy" 2>/dev/null; then
  echo "[start-local] 安装依赖..."
  pip install -q --upgrade pip
  pip install -q -r mediacrawler/requirements.txt
  pip install -q -r requirements.txt
fi

# 3. playwright browser
if ! python -c "from playwright.sync_api import sync_playwright; sync_playwright().start().chromium.executable_path" 2>/dev/null; then
  echo "[start-local] 安装 Playwright Chromium..."
  python -m playwright install chromium
fi

# 4. env：连本地暴露的 nev-mysql
export MYSQL_DB_HOST=${MYSQL_DB_HOST:-localhost}
export MYSQL_DB_PORT=${MYSQL_DB_PORT:-3306}
export MYSQL_DB_USER=${MYSQL_DB_USER:-root}
export MYSQL_DB_PWD=${MYSQL_DB_PWD:-nev123456}
export MYSQL_DB_NAME=${MYSQL_DB_NAME:-nev_insight}

# HEADLESS=no → 弹出浏览器，能扫码；改 yes 是无人值守模式
export HEADLESS=${HEADLESS:-no}

# 加载根目录 .env 中的 cookie（如有）
if [ -f ../.env ]; then
  set -a
  # shellcheck disable=SC1091
  source ../.env
  set +a
fi

echo "[start-local] DB → $MYSQL_DB_HOST:$MYSQL_DB_PORT/$MYSQL_DB_NAME"
echo "[start-local] HEADLESS=$HEADLESS"
echo "[start-local] 启动 FastAPI on http://0.0.0.0:8091 ..."

# 注意：不开 --reload。文件改动会重启进程，丢失内存里的 task dict 和日志。
# 改完代码请手动重启： pkill -f 'uvicorn server:app' （keepalive 会自动拉起）
exec uvicorn server:app --host 0.0.0.0 --port 8091
