#!/usr/bin/env bash
# 守护脚本：让 crawler 服务一直在跑；crash/退出后自动重启。
#
# 用法：
#   后台启动：  nohup ./keepalive.sh > keepalive.log 2>&1 &
#   查看状态：  tail -f keepalive.log
#   停止：     pkill -f 'keepalive.sh' && pkill -f 'uvicorn server:app'
#
# 已运行检查：lsof -i :8091

set -uo pipefail

cd "$(dirname "$0")"

trap 'echo "[keepalive] received signal, exiting"; exit 0' SIGTERM SIGINT

while true; do
  echo "[keepalive] $(date '+%Y-%m-%d %H:%M:%S') 启动 crawler..."
  ./start-local.sh || true
  echo "[keepalive] $(date '+%Y-%m-%d %H:%M:%S') 进程退出，5 秒后重启"
  sleep 5
done
