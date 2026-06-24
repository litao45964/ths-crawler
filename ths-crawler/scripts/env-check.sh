#!/bin/bash
# ============================================================================
# ths-crawler 环境诊断脚本
# 只检查不安装，5分钟摸清环境现状
# 使用：bash scripts/env-check.sh
# ============================================================================
#
# 【诞生背景 —— 当初为何要写这个脚本】
#
# 用户原话：
#   "我是 mac 电脑，日常本地已经安装了 redis 和 mysql，
#    怎么样验证你已经生成的环境搭配脚本，避免重复下载依赖，
#    根据情况怎么调整脚本，你给个排查思路和步骤"
#
# 场景还原：
#   setup.sh 会主动安装依赖、建库建表，但用户 Mac 上早已装好 Redis 和 MySQL，
#   直接跑 setup.sh 可能重复安装或覆盖已有配置。所以需要一个"只看不改"的诊断
#   脚本：逐项检查 Python/MySQL/Redis/Java/网络 是否就绪，全部 ✅ 才安心启动，
#   有 ❌ 就对照 docs/mac-env-guide.md 排查，绝不在诊断过程中做任何写入操作。
# ============================================================================

echo "=========================================="
echo "  ths-crawler 环境诊断"
echo "=========================================="

# Python
echo ""
echo "--- Python ---"
echo -n "Python3: "
python3 --version 2>/dev/null || echo "❌ 未安装"
echo -n "pip3: "
pip3 --version 2>/dev/null | head -1 || echo "❌ 未安装"
echo -n "akshare: "
python3 -c "import akshare; print('✅ v' + akshare.__version__)" 2>/dev/null || echo "❌ 未安装（pip3 install akshare pandas）"
echo -n "pandas: "
python3 -c "import pandas; print('✅ v' + pandas.__version__)" 2>/dev/null || echo "❌ 未安装"

# MySQL
echo ""
echo "--- MySQL ---"
echo -n "MySQL进程: "
ps aux | grep -v grep | grep -q mysql && echo "✅ 运行中" || echo "❌ 未运行（brew services start mysql）"
echo -n "MySQL连接(root/root): "
mysql -h 127.0.0.1 -u root -proot -e "SELECT 1" &>/dev/null && echo "✅ 可连" || echo "❌ 连不上（检查密码或端口）"
echo -n "ths_crawler库: "
mysql -h 127.0.0.1 -u root -proot -e "USE ths_crawler; SHOW TABLES;" &>/dev/null && echo "✅ 已建库建表" || echo "❌ 需初始化（mysql -u root -p < src/main/resources/db/schema.sql）"

# Redis
echo ""
echo "--- Redis ---"
echo -n "Redis进程: "
ps aux | grep -v grep | grep -q redis && echo "✅ 运行中" || echo "❌ 未运行（brew services start redis）"
echo -n "Redis连接: "
redis-cli ping 2>/dev/null | grep -q PONG && echo "✅ PONG" || echo "❌ 连不上（检查是否启动/是否设了密码）"
echo -n "Redis密码: "
redis-cli ping &>/dev/null && echo "无密码（项目默认配置即可）" || (redis-cli -a "" ping &>/dev/null && echo "需确认密码" || echo "需确认密码")

# Java
echo ""
echo "--- Java ---"
echo -n "Java版本: "
java -version 2>&1 | head -1 || echo "❌ 未安装（需 Java 17+）"
echo -n "Maven: "
mvn -version 2>/dev/null | head -1 || echo "未安装（可用项目内 ./mvnw 替代）"

# Playwright（采集器核心依赖）
echo ""
echo "--- Playwright（采集器核心依赖）---"
echo -n "Java Playwright依赖: "
if [ -f "pom.xml" ] && grep -q "com.microsoft.playwright" pom.xml 2>/dev/null; then
    PW_VER=$(grep -A2 "com.microsoft.playwright" pom.xml | grep "<version>" | head -1 | sed "s/.*<version>//;s/<\/version>.*//" | tr -d "[:space:]")
    echo "✅ v${PW_VER}"
else
    echo "❌ pom.xml中未找到playwright依赖"
fi
echo -n "Playwright chromium浏览器: "
PW_CACHE="${HOME}/.cache/ms-playwright"
if ls ${PW_CACHE}/chromium-*/INSTALLATION_COMPLETE >/dev/null 2>&1; then
    CHROMIUM_DIRS=$(ls -d ${PW_CACHE}/chromium-*/ 2>/dev/null)
    COUNT=$(echo "$CHROMIUM_DIRS" | wc -l)
    echo "✅ 已安装${COUNT}套"
    for d in ${CHROMIUM_DIRS}; do
        VER=$(basename "$d" | sed 's/chromium-//')
        echo "   chromium-${VER}"
    done
else
    echo "❌ 未安装（执行: npx playwright install chromium）"
fi
echo -n "系统Chrome: "
if which google-chrome >/dev/null 2>&1; then
    echo "✅ $(google-chrome --version 2>/dev/null)"
elif which chromium-browser >/dev/null 2>&1; then
    echo "✅ $(chromium-browser --version 2>/dev/null)"
else
    echo "⚠️  未安装（非必须，Playwright自带chromium即可）"
fi

# 网络（可选）
echo ""
echo "--- 网络连通性 ---"
echo -n "东方财富: "
curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 https://data.eastmoney.com 2>/dev/null | grep -q "200\|301\|302" && echo "✅ 可达" || echo "❌ 不可达（检查网络/代理）"

echo ""
echo "=========================================="
echo "  诊断完成"
echo "  全部 ✅ → 可直接 mvn spring-boot:run"
echo "  有 ❌ → 参照 docs/mac-env-guide.md 排查"
echo "=========================================="
