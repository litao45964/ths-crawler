#!/bin/bash
# ==============================================================================
# ths-crawler-ui 前端 Mac本地启动脚本（幂等版）
# ==============================================================================
# 诞生背景：
#   2026-06-18，ths-crawler项目后端已有mac-bootstrap.sh，前端也需要一个。
#   Mac本地开发前端比沙箱更简单：brew装Node.js、本地有完整源码、
#   不需要coze CLI下载、不用考虑沙箱文件系统崩溃。
#   本脚本与mac-bootstrap.sh配套使用，前端dev server代理到后端8100。
#
# 与沙箱版ui-bootstrap.sh的核心差异：
#   1. 用brew安装Node.js（而非NodeSource脚本）
#   2. 源码直接读本地目录（不需要coze CLI download）
#   3. 不需要设置npm镜像（Mac本地网络通常比沙箱好）
#   4. Mac自带lsof检测端口，比ss命令更直观
#
# 技术栈：
#   React 18 + Vite 5 + TypeScript + Ant Design 5 + ECharts 5
#   深色金融主题，A股惯例正红负绿
#   前后端分离：Vite dev server(5173) → 代理到Spring Boot(8100)
#
# 使用方式：
#   bash mac-ui-bootstrap.sh              # 完整流程（环境+依赖+启动+验证）
#   bash mac-ui-bootstrap.sh --env        # 仅安装Node.js环境
#   bash mac-ui-bootstrap.sh --install    # 仅npm install
#   bash mac-ui-bootstrap.sh --start      # 仅启动dev server
#   bash mac-ui-bootstrap.sh --verify     # 仅验证前端页面
#   bash mac-ui-bootstrap.sh --stop       # 停止dev server
#   bash mac-ui-bootstrap.sh --status     # 查看当前状态
#   bash mac-ui-bootstrap.sh --reset      # 清除状态标记
#
# 前置条件：
#   1. macOS + Homebrew已安装
#   2. 项目源码在 UI_DIR 指定的本地目录下
#   3. 后端mac-bootstrap.sh已执行（Spring Boot在8100运行）
# ==============================================================================

set -e

# ===== 全局配置 =====
# 前端项目目录（按实际路径修改）
UI_DIR="${HOME}/Projects/ths-crawler-ui"
# Vite dev server端口
UI_PORT=5173
# 后端端口（Vite代理目标）
BACKEND_PORT=8100
# PID文件
UI_PID_FILE="/tmp/ths-crawler-ui.pid"
# 状态标记目录
STATE_DIR="/tmp/ths-crawler-ui/.state"
# npm镜像（Mac本地通常不需要换，如遇网络问题可改为淘宝镜像）
# NPM_REGISTRY="https://registry.npmmirror.com"

# ===== M1芯片Homebrew路径 =====
if [ -x "/opt/homebrew/bin/brew" ]; then
    HOMEBREW_PREFIX="/opt/homebrew"
elif [ -x "/usr/local/bin/brew" ]; then
    HOMEBREW_PREFIX="/usr/local"
else
    HOMEBREW_PREFIX=""
fi

# ===== 颜色输出 =====
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }
log_skip()  { echo -e "${CYAN}[SKIP]${NC} $1"; }

# ===== 幂等状态管理 =====
state_done() {
    mkdir -p "${STATE_DIR}"
    echo "$(date '+%Y-%m-%d %H:%M:%S')" > "${STATE_DIR}/$1"
}

state_check() {
    [ -f "${STATE_DIR}/$1" ]
}

state_reset() {
    rm -f "${STATE_DIR}/$1"
}

# ==============================================================================
# 环境状态检查
# ==============================================================================
check_status() {
    echo ""
    echo "====== ths-crawler-ui Mac前端状态 ======"
    echo ""

    # Node.js
    if command -v node &>/dev/null; then
        local node_major=$(node --version | grep -oP '\d+' | head -1)
        if [ "${node_major}" -ge 18 ]; then
            echo -e "  Node.js:   ${GREEN}✓ $(node --version)${NC}"
        else
            echo -e "  Node.js:   ${YELLOW}△ $(node --version)（需要18+）${NC}"
        fi
    else
        echo -e "  Node.js:   ${RED}✗ 未安装${NC}"
    fi

    # npm
    if command -v npm &>/dev/null; then
        echo -e "  npm:       ${GREEN}✓ $(npm --version)${NC}"
    else
        echo -e "  npm:       ${RED}✗ 未安装${NC}"
    fi

    # 源码
    if [ -f "${UI_DIR}/package.json" ]; then
        local src_count=$(find "${UI_DIR}/src" -type f \( -name "*.tsx" -o -name "*.ts" -o -name "*.css" \) 2>/dev/null | wc -l)
        echo -e "  源码:      ${GREEN}✓ ${src_count}个源文件 (${UI_DIR})${NC}"
    else
        echo -e "  源码:      ${RED}✗ ${UI_DIR}/package.json 不存在${NC}"
    fi

    # node_modules
    if [ -d "${UI_DIR}/node_modules" ]; then
        local mod_count=$(ls "${UI_DIR}/node_modules" 2>/dev/null | wc -l)
        echo -e "  依赖:      ${GREEN}✓ ${mod_count}个包${NC}"
    else
        echo -e "  依赖:      ${RED}✗ 未安装（执行 --install）${NC}"
    fi

    # dev server
    if [ -f "${UI_PID_FILE}" ] && kill -0 "$(cat ${UI_PID_FILE})" 2>/dev/null; then
        echo -e "  Dev Server:${GREEN}✓ 运行中 PID=$(cat ${UI_PID_FILE}) 端口=${UI_PORT}${NC}"
    else
        echo -e "  Dev Server:${YELLOW}△ 未运行${NC}"
    fi

    # 后端
    if curl -s "http://localhost:${BACKEND_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "."; then
        echo -e "  后端:      ${GREEN}✓ ${BACKEND_PORT}端口有响应${NC}"
    else
        echo -e "  后端:      ${YELLOW}△ ${BACKEND_PORT}端口无响应（前端可启动但API不可用）${NC}"
    fi

    echo ""
    echo "========================================"
}

# ==============================================================================
# 阶段1：安装Node.js（brew）
# ==============================================================================
# Mac安装Node.js有3种方式：brew / nvm / 官网pkg
# 这里用brew最简单，和后端风格一致。
# brew install node自带npm，不需要单独装。
# 检测逻辑：node存在且版本≥18
# ==============================================================================
setup_environment() {
    log_step "========== 阶段1：环境搭建（幂等）=========="

    # --- 1.1 Homebrew自检 ---
    if ! command -v brew &>/dev/null; then
        log_error "Homebrew未安装！请先执行: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        return 1
    fi

    # --- 1.2 Node.js ---
    if command -v node &>/dev/null; then
        local node_major=$(node --version | grep -oP '\d+' | head -1)
        if [ "${node_major}" -ge 18 ]; then
            log_skip "Node.js $(node --version) 已安装"
        else
            log_warn "Node.js版本过低($(node --version))，Vite 5要求18+，升级中..."
            brew upgrade node 2>&1 | tail -5
        fi
    else
        log_info "安装Node.js（brew install node，自带npm）..."
        brew install node 2>&1 | tail -5
        log_info "Node.js 安装完成: $(node --version)"
    fi

    # --- 1.3 验证 ---
    log_info "Node.js: $(node --version), npm: $(npm --version)"

    state_done "env_setup"
    log_step "========== 阶段1完成：环境就绪 =========="
}

# ==============================================================================
# 阶段2：npm install
# ==============================================================================
# Mac本地不需要从项目空间下载源码，本地已有完整项目目录。
# 直接在项目目录下执行npm install即可。
# 幂等逻辑：node_modules已存在且包数>50则跳过。
# ==============================================================================
install_deps() {
    log_step "========== 阶段2：安装依赖（幂等）=========="

    # 检查项目目录
    if [ ! -f "${UI_DIR}/package.json" ]; then
        log_error "项目目录不存在: ${UI_DIR}/package.json"
        log_error "请确认UI_DIR配置是否正确"
        return 1
    fi

    cd "${UI_DIR}"

    # --- 2.0 幂等检测 ---
    if [ -d "node_modules" ]; then
        local mod_count=$(ls node_modules 2>/dev/null | wc -l)
        if [ "${mod_count}" -gt 50 ]; then
            log_skip "node_modules已存在（${mod_count}个包），跳过安装。如需重装: rm -rf node_modules && bash mac-ui-bootstrap.sh --install"
            return
        else
            log_warn "node_modules不完整（仅${mod_count}个包），重新安装..."
        fi
    fi

    # --- 2.1 可选：设置npm镜像 ---
    # Mac本地通常不需要，如遇网络问题取消下面注释
    # npm config set registry "${NPM_REGISTRY}" 2>/dev/null || true

    # --- 2.2 npm install ---
    log_info "开始npm install..."
    npm install --legacy-peer-deps 2>&1 | tail -10

    # --- 2.3 验证 ---
    if [ -d "node_modules" ]; then
        local mod_count=$(ls node_modules 2>/dev/null | wc -l)
        local react_ok=false antd_ok=false vite_ok=false
        [ -d "node_modules/react" ] && react_ok=true
        [ -d "node_modules/antd" ] && antd_ok=true
        [ -d "node_modules/vite" ] && vite_ok=true

        if [ "${react_ok}" = true ] && [ "${antd_ok}" = true ] && [ "${vite_ok}" = true ]; then
            log_info "npm install完成！${mod_count}个包，核心依赖均已就位"
        else
            log_warn "npm install完成但有核心依赖缺失: react=${react_ok} antd=${antd_ok} vite=${vite_ok}"
        fi
    else
        log_error "npm install失败！"
        return 1
    fi

    state_done "npm_install"
    log_step "========== 阶段2完成：依赖就绪 =========="
}

# ==============================================================================
# 阶段3：启动Vite dev server
# ==============================================================================
start_dev() {
    log_step "========== 阶段3：启动dev server（幂等）=========="

    # 检查项目目录
    if [ ! -f "${UI_DIR}/package.json" ]; then
        log_error "项目目录不存在: ${UI_DIR}/package.json"
        return 1
    fi

    cd "${UI_DIR}"

    # --- 3.0 幂等检测 ---
    if [ -f "${UI_PID_FILE}" ] && kill -0 "$(cat ${UI_PID_FILE})" 2>/dev/null; then
        log_skip "dev server已在运行(PID=$(cat ${UI_PID_FILE}), 端口=${UI_PORT})"
        return
    fi

    if curl -s "http://localhost:${UI_PORT}" 2>/dev/null | grep -q "."; then
        log_skip "检测到端口${UI_PORT}有服务在响应，跳过启动"
        return
    fi

    # --- 3.1 清理旧进程 ---
    if [ -f "${UI_PID_FILE}" ]; then
        local old_pid=$(cat "${UI_PID_FILE}")
        kill "${old_pid}" 2>/dev/null || true
        rm -f "${UI_PID_FILE}"
    fi

    if lsof -ti:${UI_PORT} 2>/dev/null | grep -q .; then
        log_warn "端口${UI_PORT}被占用，尝试释放..."
        lsof -ti:${UI_PORT} | xargs kill 2>/dev/null || true
        sleep 2
    fi

    # --- 3.2 检查后端状态 ---
    if curl -s "http://localhost:${BACKEND_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "."; then
        log_info "后端${BACKEND_PORT}端口有响应，API代理将正常工作"
    else
        log_warn "后端${BACKEND_PORT}端口无响应！前端可启动但API数据不可用"
        log_warn "请先启动后端: bash mac-bootstrap.sh --start"
    fi

    # --- 3.3 启动Vite dev server ---
    # Mac本地 --host 不是必须的，但方便同一网络其他设备访问
    log_info "启动Vite dev server..."
    nohup npx vite --host > /tmp/ths-crawler-ui.log 2>&1 &
    local pid=$!
    echo "${pid}" > "${UI_PID_FILE}"

    # --- 3.4 等待启动 ---
    log_info "等待dev server启动（最多30秒）..."
    local started=false
    for i in $(seq 1 30); do
        if curl -s "http://localhost:${UI_PORT}" 2>/dev/null | grep -q "."; then
            started=true
            break
        fi
        if grep -q "ready in" /tmp/ths-crawler-ui.log 2>/dev/null; then
            started=true
            break
        fi
        sleep 1
    done

    if [ "${started}" = true ]; then
        log_info "dev server启动成功！PID=${pid}，端口=${UI_PORT}"
        log_info "访问地址: http://localhost:${UI_PORT}"
    else
        log_error "dev server启动超时！查看日志: tail -20 /tmp/ths-crawler-ui.log"
        return 1
    fi

    state_done "dev_started"
    log_step "========== 阶段3完成：前端运行中 =========="
}

# ==============================================================================
# 阶段4：验证前端页面
# ==============================================================================
verify_frontend() {
    log_step "========== 阶段4：前端验证 =========="

    local BASE="http://localhost:${UI_PORT}"

    # --- 4.1 首页HTML ---
    log_info "检查首页..."
    local html=$(curl -s "${BASE}/" 2>/dev/null)
    if echo "${html}" | grep -q "root"; then
        log_info "✓ 首页HTML正常"
    else
        log_warn "✗ 首页HTML异常"
    fi

    # --- 4.2 Vite HMR ---
    log_info "检查Vite资源..."
    if curl -s -o /dev/null -w "%{http_code}" "${BASE}/@vite/client" 2>/dev/null | grep -q "200"; then
        log_info "✓ Vite HMR客户端正常"
    else
        log_warn "✗ Vite HMR客户端异常"
    fi

    # --- 4.3 API代理 ---
    log_info "检查API代理..."
    local api_status=$(curl -s -o /dev/null -w "%{http_code}" "${BASE}/api/industry-flow/latest?topN=1" 2>/dev/null)
    if [ "${api_status}" = "200" ]; then
        log_info "✓ API代理正常（后端返回200）"
    elif [ "${api_status}" = "502" ] || [ "${api_status}" = "504" ]; then
        log_warn "✗ API代理返回${api_status}（后端未启动？）"
    else
        log_warn "△ API代理返回${api_status}"
    fi

    # --- 4.4 浏览器提示 ---
    echo ""
    log_info "===== 前端验证总结 ====="
    log_info "前端地址: http://localhost:${UI_PORT}"
    log_info "API代理:  http://localhost:${UI_PORT}/api → http://localhost:${BACKEND_PORT}/api"
    log_info "请在浏览器打开 http://localhost:${UI_PORT} 查看完整页面"

    log_step "========== 阶段4完成：前端验证完毕 =========="
}

# ==============================================================================
# 停止dev server
# ==============================================================================
stop_dev() {
    if [ -f "${UI_PID_FILE}" ]; then
        local pid=$(cat "${UI_PID_FILE}")
        if kill -0 "${pid}" 2>/dev/null; then
            kill "${pid}" 2>/dev/null || true
            sleep 1
            # 如果还在跑，强杀
            kill -9 "${pid}" 2>/dev/null || true
            log_info "dev server已停止(PID=${pid})"
        fi
        rm -f "${UI_PID_FILE}"
    else
        if lsof -ti:${UI_PORT} 2>/dev/null | grep -q .; then
            lsof -ti:${UI_PORT} | xargs kill 2>/dev/null
            log_info "端口${UI_PORT}进程已终止"
        else
            log_info "无运行中的dev server"
        fi
    fi
    state_reset "dev_started"
}

# ==============================================================================
# 重置状态
# ==============================================================================
reset_state() {
    rm -rf "${STATE_DIR}"
    log_info "所有状态标记已清除，下次执行将重新走全流程"
}

# ==============================================================================
# 主入口
# ==============================================================================
case "${1}" in
    --env)
        setup_environment
        ;;
    --install)
        install_deps
        ;;
    --start)
        start_dev
        ;;
    --verify)
        verify_frontend
        ;;
    --stop)
        stop_dev
        ;;
    --status)
        check_status
        ;;
    --reset)
        reset_state
        ;;
    --all|"")
        setup_environment
        install_deps
        start_dev
        verify_frontend
        ;;
    *)
        echo "用法: bash mac-ui-bootstrap.sh [选项]"
        echo ""
        echo "选项:"
        echo "  (无参数)    完整流程（环境+依赖+启动+验证）"
        echo "  --env       仅安装Node.js环境"
        echo "  --install   仅npm install"
        echo "  --start     仅启动Vite dev server"
        echo "  --verify    仅验证前端页面"
        echo "  --stop      停止dev server"
        echo "  --status    查看当前状态"
        echo "  --reset     清除状态标记"
        echo ""
        echo "配置项（脚本顶部修改）:"
        echo "  UI_DIR=${UI_DIR}"
        echo "  UI_PORT=${UI_PORT}"
        echo "  BACKEND_PORT=${BACKEND_PORT}"
        echo ""
        echo "依赖关系："
        echo "  前端API代理需要后端Spring Boot在${BACKEND_PORT}端口运行。"
        echo "  如后端未启动，前端页面可打开但无数据。"
        echo "  建议先执行: bash mac-bootstrap.sh（后端一键启动）"
        exit 1
        ;;
esac
