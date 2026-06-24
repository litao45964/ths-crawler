#!/bin/bash
# ============================================================
# shellzdh-cloud - 云电脑一键同步部署脚本
# 用法: bash shellzdh-cloud.sh
# 前提: 已在 /root/ths-crawler-repo 目录下执行过 git pull
# 流程: git pull → 打包 → 重启服务 → 健康检查
# ============================================================

set -e

# ---------- 配置 ----------
REPO_DIR="/root/ths-crawler-repo"
JAVA_DIR="$REPO_DIR/ths-crawler"
SERVICE_SCRIPT="/root/ths-service.sh"
APP_PORT=8100

# ---------- 颜色 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn()  { echo -e "${YELLOW}[!]${NC} $1"; }
fail()  { echo -e "${RED}[✗]${NC} $1"; exit 1; }
step()  { echo -e "\n${BLUE}=== [$1] ===${NC}"; }

# ============================================================
# 步骤1: Git pull
# ============================================================
step "1/4 Git pull"
cd "$REPO_DIR"
BEFORE=$(git rev-parse --short HEAD)
if git pull origin main 2>&1; then
    AFTER=$(git rev-parse --short HEAD)
    if [ "$BEFORE" = "$AFTER" ]; then
        info "已是最新: $AFTER"
    else
        info "更新: $BEFORE → $AFTER"
    fi
else
    fail "git pull失败，检查网络"
fi

# ============================================================
# 步骤2: Maven打包
# ============================================================
step "2/4 Maven打包"
cd "$JAVA_DIR"
if mvn clean package -DskipTests -q 2>&1; then
    info "打包成功"
else
    fail "打包失败，检查编译错误"
fi

JAR_FILE="$JAVA_DIR/target/ths-crawler-1.0.0-SNAPSHOT.jar"
[ -f "$JAR_FILE" ] || fail "jar文件未生成"

# ============================================================
# 步骤3: 重启服务
# ============================================================
step "3/4 重启服务"
if [ -x "$SERVICE_SCRIPT" ]; then
    bash "$SERVICE_SCRIPT" restart
    info "服务重启命令已执行"
else
    # 兜底：手动kill+启动
    warn "ths-service.sh不可用，手动重启"
    pkill -f "ths-crawler-1.0.0-SNAPSHOT.jar" 2>/dev/null || true
    sleep 2
    nohup java -jar "$JAR_FILE" --spring.datasource.password=root \
        > /root/ths-crawler-app.log 2>&1 &
    info "Spring Boot已启动 (PID: $!)"
fi

# ============================================================
# 步骤4: 健康检查
# ============================================================
step "4/4 健康检查"
echo -n "等待服务就绪"
for i in $(seq 1 30); do
    if curl -s "http://localhost:$APP_PORT/api/industry-flow/latest?topN=1" > /dev/null 2>&1; then
        echo ""
        info "服务就绪 ✅ (port $APP_PORT)"
        break
    fi
    if [ "$i" = "30" ]; then
        echo ""
        fail "30秒内服务未就绪，检查日志: tail -f /root/ths-crawler-app.log"
    fi
    echo -n "."
    sleep 1
done

# ============================================================
# 完成
# ============================================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "🚀 云电脑部署完成！"
echo ""
echo "  API验证:  curl -s http://localhost:$APP_PORT/api/industry-flow/latest?topN=3"
echo "  服务状态: bash $SERVICE_SCRIPT status"
echo "  应用日志: tail -f /root/ths-crawler-app.log"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
