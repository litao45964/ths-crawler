#!/bin/bash
# 集成测试执行脚本：杀残留浏览器进程 + timeout 5分钟兜底
# 用法: ./run-it.sh [debug-html]

set -e
cd "$(dirname "$0")"

echo "🧹 清理残留浏览器进程..."
pkill -f chromium 2>/dev/null || true
pkill -f "playwright" 2>/dev/null || true
sleep 1

DEBUG_FLAG=""
if [ "$1" = "debug-html" ]; then
    DEBUG_FLAG="-Dths.crawler.debug-html=true"
    echo "📄 HTML 留档已开启"
fi

echo "🚀 开始集成测试（超时 5 分钟）..."
timeout 300 mvn verify -pl . -Dtest=ThsIndustryFetcherIT -DfailIfNoTests=false $DEBUG_FLAG 2>&1 | grep -E "(💾|原始 HTML|BUILD|Tests run.*ThsIndustryFetcherIT|前5条|后5条|领涨|✅|❌)"

EXIT_CODE=${PIPESTATUS[0]}
if [ $EXIT_CODE -eq 124 ]; then
    echo "⏰ 超时！5分钟内未完成，已强制终止"
    pkill -f chromium 2>/dev/null || true
    exit 1
elif [ $EXIT_CODE -eq 0 ]; then
    echo "✅ 集成测试完成"
else
    echo "❌ 集成测试失败 (exit=$EXIT_CODE)"
    exit $EXIT_CODE
fi
