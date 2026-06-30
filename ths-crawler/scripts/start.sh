#!/bin/bash
# ============================================================
# ths-crawler 幂等启动脚本
# 功能：启动/重启/停止前后端服务，已运行则跳过
#
# 用法：
#   bash start.sh            # 启动（幂等，已运行则跳过，默认）
#   bash start.sh restart    # 重启（先停后启）
#   bash start.sh stop       # 停止所有服务
#   bash start.sh status     # 查看运行状态
# ============================================================

set -euo pipefail

# ---- 配置 ----
BACKEND_DIR="/Users/litao/Documents/devtools/study/ths-crawler/ths-crawler"
FRONTEND_DIR="/Users/litao/Documents/devtools/study/ths-crawler/ths-crawler-ui"
BACKEND_PORT=8100
FRONTEND_PORT=5173
BACKEND_LOG="/tmp/ths-backend.log"
FRONTEND_LOG="/tmp/ths-frontend.log"
MYSQL_PASSWORD="Litao45964"
MAVEN_SETTINGS="/Users/litao/.m2/settings-public.xml"
# JAVA_HOME 已持久化到 ~/.zshrc，此处作为兜底
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home}"

# ---- 颜色 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

GAP="============================================================"

# ---- 工具函数 ----
log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${CYAN}[STEP]${NC}  $1"; }

# 检查端口是否被监听
port_listening() {
    lsof -i ":$1" -P -sTCP:LISTEN > /dev/null 2>&1
}

# 获取端口上的 PID
port_pid() {
    lsof -i ":$1" -P -sTCP:LISTEN -t 2>/dev/null | head -1
}

# 等待端口就绪
wait_port() {
    local port=$1 timeout=${2:-60} elapsed=0
    while [ $elapsed -lt $timeout ]; do
        if port_listening "$port"; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

# ---- 动作函数 ----

# 检查并启动基础设施
ensure_infra() {
    log_step "检查基础设施..."

    # MySQL — LaunchDaemon 系统自启动（/usr/local/mysql/bin/mysqld）
    if mysql -u root -p"$MYSQL_PASSWORD" -e "SELECT 1" > /dev/null 2>&1; then
        log_info "MySQL  已连接 (LaunchDaemon 自启动)"
    else
        log_warn "MySQL 未运行，尝试通过 launchctl 启动..."
        sudo launchctl load /Library/LaunchDaemons/com.oracle.oss.mysql.mysqld.plist 2>/dev/null || true
        sleep 3
        if mysql -u root -p"$MYSQL_PASSWORD" -e "SELECT 1" > /dev/null 2>&1; then
            log_info "MySQL  已启动"
        else
            log_error "MySQL 启动失败，请检查: sudo launchctl list | grep mysql"
        fi
    fi

    # 创建数据库
    mysql -u root -p"$MYSQL_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS ths_crawler DEFAULT CHARSET utf8mb4" 2>/dev/null
    log_info "MySQL  数据库 ths_crawler 已就绪"

    # Redis — 用户态进程 /usr/local/bin/redis-server（软链接）
    if redis-cli ping > /dev/null 2>&1; then
        log_info "Redis  已连接 (PONG)"
    else
        log_warn "Redis 未运行，尝试启动..."
        nohup /usr/local/bin/redis-server > ~/redis.log 2>&1 &
        sleep 2
        if redis-cli ping > /dev/null 2>&1; then
            log_info "Redis  已启动"
        else
            log_error "Redis 启动失败，请检查: pgrep -f redis-server"
        fi
    fi
}

# 启动后端
start_backend() {
    if port_listening $BACKEND_PORT; then
        local pid
        pid=$(port_pid $BACKEND_PORT)
        log_info "后端   已在运行 (端口 $BACKEND_PORT, PID $pid)，跳过"
        return 0
    fi

    log_step "启动后端 (端口 $BACKEND_PORT)..."
    cd "$BACKEND_DIR"
    mvn spring-boot:run \
        --settings "$MAVEN_SETTINGS" \
        > "$BACKEND_LOG" 2>&1 &
    local mvn_pid=$!

    if wait_port $BACKEND_PORT 90; then
        log_info "后端   启动成功 (PID $mvn_pid, 端口 $BACKEND_PORT)"
    else
        log_error "后端   启动超时，查看日志: tail -50 $BACKEND_LOG"
    fi
}

# 启动前端
start_frontend() {
    # 确保依赖已安装
    if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
        log_step "安装前端依赖..."
        cd "$FRONTEND_DIR"
        npm install > /tmp/ths-frontend-install.log 2>&1
        log_info "前端   依赖安装完成"
    fi

    if port_listening $FRONTEND_PORT; then
        local pid
        pid=$(port_pid $FRONTEND_PORT)
        log_info "前端   已在运行 (端口 $FRONTEND_PORT, PID $pid)，跳过"
        return 0
    fi

    log_step "启动前端 (端口 $FRONTEND_PORT)..."
    cd "$FRONTEND_DIR"
    npx vite --host > "$FRONTEND_LOG" 2>&1 &
    local vite_pid=$!

    if wait_port $FRONTEND_PORT 30; then
        log_info "前端   启动成功 (PID $vite_pid, 端口 $FRONTEND_PORT)"
    else
        log_error "前端   启动超时，查看日志: tail -50 $FRONTEND_LOG"
    fi
}

# 停止后端
stop_backend() {
    if port_listening $BACKEND_PORT; then
        local pid
        pid=$(port_pid $BACKEND_PORT)
        log_step "停止后端 (PID $pid)..."
        kill "$pid" 2>/dev/null || true
        sleep 2
        # 强力清理：杀掉 Maven 启动的子进程
        pkill -f "spring-boot:run" 2>/dev/null || true
        pkill -f "ThsCrawlerApplication" 2>/dev/null || true

        if port_listening $BACKEND_PORT; then
            log_warn "后端   仍在运行，尝试强制停止..."
            kill -9 "$pid" 2>/dev/null || true
            pkill -9 -f "spring-boot:run" 2>/dev/null || true
        fi
        log_info "后端   已停止"
    else
        log_info "后端   未运行，跳过"
    fi
}

# 停止前端
stop_frontend() {
    if port_listening $FRONTEND_PORT; then
        local pid
        pid=$(port_pid $FRONTEND_PORT)
        log_step "停止前端 (PID $pid)..."
        kill "$pid" 2>/dev/null || true
        pkill -f "vite" 2>/dev/null || true
        sleep 1

        if port_listening $FRONTEND_PORT; then
            kill -9 "$pid" 2>/dev/null || true
            pkill -9 -f "vite" 2>/dev/null || true
        fi
        log_info "前端   已停止"
    else
        log_info "前端   未运行，跳过"
    fi
}

# 查看状态
show_status() {
    echo ""
    echo -e "${CYAN}$GAP${NC}"
    echo -e "${CYAN}  ths-crawler 服务状态${NC}"
    echo -e "${CYAN}$GAP${NC}"

    # 后端
    if port_listening $BACKEND_PORT; then
        local pid
        pid=$(port_pid $BACKEND_PORT)
        echo -e "  后端 (:$BACKEND_PORT)   ${GREEN}● 运行中${NC}   PID: $pid"
    else
        echo -e "  后端 (:$BACKEND_PORT)   ${RED}○ 已停止${NC}"
    fi

    # 前端
    if port_listening $FRONTEND_PORT; then
        local pid
        pid=$(port_pid $FRONTEND_PORT)
        echo -e "  前端 (:$FRONTEND_PORT)   ${GREEN}● 运行中${NC}   PID: $pid"
    else
        echo -e "  前端 (:$FRONTEND_PORT)   ${RED}○ 已停止${NC}"
    fi

    # MySQL
    if mysql -u root -p"$MYSQL_PASSWORD" -e "SELECT 1" > /dev/null 2>&1; then
        echo -e "  MySQL  (3306)    ${GREEN}● 运行中${NC}"
    else
        echo -e "  MySQL  (3306)    ${RED}○ 已停止${NC}"
    fi

    # Redis
    if redis-cli ping > /dev/null 2>&1; then
        echo -e "  Redis  (6379)    ${GREEN}● 运行中${NC}"
    else
        echo -e "  Redis  (6379)    ${RED}○ 已停止${NC}"
    fi

    echo -e "${CYAN}$GAP${NC}"
    echo ""

    # API 冒烟
    if port_listening $BACKEND_PORT; then
        local api_resp
        api_resp=$(curl -s --max-time 3 "http://localhost:$BACKEND_PORT/api/industry-flow/latest?topN=1" 2>/dev/null || true)
        if echo "$api_resp" | grep -q '"success"'; then
            echo -e "  API 冒烟测试:  ${GREEN}✓${NC}  $api_resp"
        else
            echo -e "  API 冒烟测试:  ${RED}✗${NC}  响应异常"
        fi
    fi

    echo ""
    echo -e "  访问地址:  ${CYAN}http://localhost:$FRONTEND_PORT${NC}"
    echo ""
}

# ---- 主入口 ----
ACTION="${1:-start}"

case "$ACTION" in
    start)
        echo -e "${CYAN}$GAP${NC}"
        echo -e "${CYAN}  ths-crawler 幂等启动${NC}"
        echo -e "${CYAN}$GAP${NC}"
        echo ""
        ensure_infra
        echo ""
        start_backend
        echo ""
        start_frontend
        echo ""
        show_status
        ;;
    restart)
        echo -e "${CYAN}$GAP${NC}"
        echo -e "${CYAN}  ths-crawler 重启${NC}"
        echo -e "${CYAN}$GAP${NC}"
        echo ""
        stop_backend
        stop_frontend
        echo ""
        ensure_infra
        echo ""
        start_backend
        echo ""
        start_frontend
        echo ""
        show_status
        ;;
    stop)
        echo -e "${YELLOW}$GAP${NC}"
        echo -e "${YELLOW}  ths-crawler 停止${NC}"
        echo -e "${YELLOW}$GAP${NC}"
        echo ""
        stop_backend
        stop_frontend
        echo ""
        show_status
        ;;
    status)
        show_status
        ;;
    *)
        echo "用法: bash start.sh [start|restart|stop|status]"
        echo "  start   - 幂等启动（默认）"
        echo "  restart - 先停后启"
        echo "  stop    - 停止所有服务"
        echo "  status  - 查看运行状态"
        exit 1
        ;;
esac
