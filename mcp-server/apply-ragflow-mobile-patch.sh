#!/bin/bash
# 把 RAGFlow share 页的 mobile CSS patch 应用到 docker-ragflow-cpu-1 容器
# 用法：./apply-ragflow-mobile-patch.sh
#
# 何时跑：
#   - 首次启用时
#   - RAGFlow 容器 down && up 重建后（因为 patch 写在容器内）
#   - RAGFlow 升级到新版后

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CSS="$SCRIPT_DIR/ragflow-mobile-patch.css"
CONTAINER="docker-ragflow-cpu-1"

[ -f "$CSS" ] || { echo "❌ 找不到 $CSS"; exit 1; }

if ! docker ps --format '{{.Names}}' | grep -q "^$CONTAINER$"; then
    echo "❌ 容器 $CONTAINER 未运行"
    exit 1
fi

echo "📦 拷 mobile-patch.css 到容器..."
docker cp "$CSS" "$CONTAINER:/ragflow/web/dist/mobile-patch.css"

echo "🔧 patch dist/index.html 注入 <link>..."
docker exec "$CONTAINER" sh -c '
    [ -f /ragflow/web/dist/index.html.orig ] || cp /ragflow/web/dist/index.html /ragflow/web/dist/index.html.orig
    cp /ragflow/web/dist/index.html.orig /ragflow/web/dist/index.html
    TS=$(date +%s)
    sed -i "s|</head>|<link rel=\"stylesheet\" href=\"/mobile-patch.css?v=$TS\"></head>|" /ragflow/web/dist/index.html
    grep -q mobile-patch.css /ragflow/web/dist/index.html
' && echo "✅ 注入成功"

echo "🧪 验证：访问 http://localhost/mobile-patch.css"
code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/mobile-patch.css)
[ "$code" = "200" ] && echo "✅ CSS 可访问 (HTTP $code)" || { echo "❌ CSS 404 ($code)"; exit 1; }

echo ""
echo "🎉 Mobile patch 已生效，刷新 RAGFlow share 页（移动端 ≤768px）即可看到效果"
echo "💡 测试 URL：http://localhost/agent/share?shared_id=...&from=agent&auth=...&visible_avatar=1"
