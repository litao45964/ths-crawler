#!/bin/bash
# ==============================================================================
# ths-crawler-ui 前端沙箱一键启动脚本（幂等版）
# ==============================================================================
# 诞生背景：
#   2026-06-18，ths-crawler项目后端已有sandbox-bootstrap.sh实现幂等启动，
#   但前端项目(ths-crawler-ui)还靠手动下载+npm install+启动，每次沙箱重启
#   都要重新走一遍。前端React项目依赖npm，node_modules动辄几百MB，
#   重复安装既耗时又浪费带宽。特仿照后端脚本，为前端也写一个幂等版。
#
# 核心设计原则：幂等性
#   每一步都先检测当前状态，已满足的就跳过。
#   Node.js已装？跳过。node_modules已存在？跳过npm install。
#   dev server已在运行？跳过启动。
#   目标：无论沙箱什么状态，跑一遍都能到终态。
#
# 技术栈：
#   React 18 + Vite 5 + TypeScript + Ant Design 5 + ECharts 5
#   深色金融主题（#0d1520背景），A股惯例正红负绿
#   前后端分离：Vite dev server(5173) → 代理到Spring Boot后端(8100)
#
# 使用方式：
#   bash ui-bootstrap.sh              # 完整搭建（环境+代码+依赖+启动+验证）
#   bash ui-bootstrap.sh --env        # 仅安装Node.js环境
#   bash ui-bootstrap.sh --code       # 仅下载源码
#   bash ui-bootstrap.sh --install    # 仅npm install
#   bash ui-bootstrap.sh --start      # 仅启动dev server
#   bash ui-bootstrap.sh --verify     # 仅验证前端页面可访问
#   bash ui-bootstrap.sh --stop       # 停止dev server
#   bash ui-bootstrap.sh --status     # 查看当前状态
#   bash ui-bootstrap.sh --reset      # 清除状态标记
#
# 前置条件：
#   1. 沙箱能访问外网（apt-get / npm registry）
#   2. coze CLI已安装并认证
#   3. 项目ID: 7652034661715165474
#   4. 后端sandbox-bootstrap.sh已执行（Spring Boot在8100运行）
# ==============================================================================

set -e

# ===== 全局配置 =====
PROJECT_ID="7652034661715165474"
# 前端项目目录
BUILD_DIR="/tmp/ths-crawler-ui"
# Vite dev server端口
UI_PORT=5173
# 后端端口（Vite代理目标）
BACKEND_PORT=8100
# PID文件
UI_PID_FILE="${BUILD_DIR}/ui.pid"
# 状态标记目录
STATE_DIR="${BUILD_DIR}/.state"
# npm镜像（沙箱在国内，用淘宝镜像加速）
NPM_REGISTRY="https://registry.npmmirror.com"

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
    echo "====== ths-crawler-ui 前端沙箱状态 ======"
    echo ""

    # Node.js
    if command -v node &>/dev/null; then
        echo -e "  Node.js:   ${GREEN}✓ $(node --version)${NC}"
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
    if [ -f "${BUILD_DIR}/package.json" ]; then
        local src_count=$(find "${BUILD_DIR}/src" -name "*.tsx" -o -name "*.ts" -o -name "*.css" 2>/dev/null | wc -l)
        echo -e "  源码:      ${GREEN}✓ ${src_count}个源文件${NC}"
    else
        echo -e "  源码:      ${RED}✗ 未下载${NC}"
    fi

    # node_modules
    if [ -d "${BUILD_DIR}/node_modules" ]; then
        local mod_count=$(ls "${BUILD_DIR}/node_modules" 2>/dev/null | wc -l)
        echo -e "  依赖:      ${GREEN}✓ ${mod_count}个包${NC}"
    else
        echo -e "  依赖:      ${RED}✗ 未安装${NC}"
    fi

    # dev server
    if [ -f "${UI_PID_FILE}" ] && kill -0 "$(cat ${UI_PID_FILE})" 2>/dev/null; then
        echo -e "  Dev Server:${GREEN}✓ 运行中 PID=$(cat ${UI_PID_FILE}) 端口=${UI_PORT}${NC}"
    else
        echo -e "  Dev Server:${YELLOW}△ 未运行${NC}"
    fi

    # 后端（前端代理依赖后端）
    if curl -s "http://localhost:${BACKEND_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "."; then
        echo -e "  后端:      ${GREEN}✓ ${BACKEND_PORT}端口有响应${NC}"
    else
        echo -e "  后端:      ${YELLOW}△ ${BACKEND_PORT}端口无响应（前端可启动但API不可用）${NC}"
    fi

    # 已完成的状态标记
    if [ -d "${STATE_DIR}" ]; then
        echo ""
        echo "  已完成的步骤:"
        for f in ${STATE_DIR}/*; do
            [ -f "$f" ] && echo -e "    ${CYAN}$(basename $f)${NC} @ $(cat $f)"
        done
    fi

    echo ""
    echo "========================================"
}

# ==============================================================================
# 阶段1：环境搭建（Node.js + npm）
# ==============================================================================
# 沙箱Ubuntu 22.04默认没有Node.js，需要手动安装。
# 选择NodeSource的Node 18 LTS（Vite 5要求Node 18+）。
# 检测逻辑：node命令存在且版本≥18
# ==============================================================================
setup_environment() {
    log_step "========== 阶段1：环境搭建（幂等）=========="

    # --- 1.1 清理apt锁 ---
    if fuser /var/lib/dpkg/lock-frontend 2>/dev/null; then
        log_info "检测到apt锁进程，等待释放..."
        sleep 5
    fi
    rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock 2>/dev/null || true
    dpkg --configure -a 2>/dev/null || true

    # --- 1.2 安装Node.js 18 LTS ---
    # NodeSource官方脚本安装，比apt默认源版本新
    # 检测：node存在且major version >= 18
    if command -v node &>/dev/null; then
        local node_major=$(node --version | grep -oP '\d+' | head -1)
        if [ "${node_major}" -ge 18 ]; then
            log_skip "Node.js $(node --version) 已安装"
        else
            log_warn "Node.js版本过低($(node --version))，需要18+，重新安装..."
            apt-get remove -y nodejs 2>/dev/null || true
            _install_nodejs
        fi
    else
        _install_nodejs
    fi

    # --- 1.3 验证 ---
    log_info "Node.js: $(node --version), npm: $(npm --version)"

    state_done "env_setup"
    log_step "========== 阶段1完成：环境就绪 =========="
}

_install_nodejs() {
    log_info "安装Node.js 18 LTS（NodeSource）..."
    apt-get update -qq 2>/dev/null || true
    apt-get install -y -qq ca-certificates curl gnupg 2>&1 | tail -3

    # NodeSource GPG key + repo
    mkdir -p /etc/apt/keyrings 2>/dev/null || true
    curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key 2>/dev/null | \
        gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg 2>/dev/null || true

    # 如果NodeSource添加失败，回退到apt默认源（版本可能旧但能用）
    if [ ! -f /etc/apt/keyrings/nodesource.gpg ]; then
        log_warn "NodeSource GPG key获取失败，回退到apt默认源..."
        apt-get install -y -qq nodejs npm 2>&1 | tail -3
        return
    fi

    echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_18.x nodistro main" \
        > /etc/apt/sources.list.d/nodesource.list 2>/dev/null || true

    apt-get update -qq 2>/dev/null || true
    apt-get install -y -qq nodejs 2>&1 | tail -5

    if ! command -v node &>/dev/null; then
        log_warn "NodeSource安装失败，回退到apt默认源..."
        apt-get install -y -qq nodejs npm 2>&1 | tail -3
    fi
}

# ==============================================================================
# 阶段2：从项目空间下载源码
# ==============================================================================
# 前端项目文件结构：
#   ths-crawler-ui/
#   ├── index.html              # Vite入口HTML
#   ├── package.json            # 依赖声明
#   ├── tsconfig.json           # TypeScript配置
#   ├── tsconfig.node.json      # Vite专用TS配置
#   ├── vite.config.ts          # Vite配置（含后端代理）
#   └── src/
#       ├── App.css             # 全局样式
#       ├── App.tsx             # 根组件
#       ├── main.tsx            # 入口文件
#       ├── vite-env.d.ts       # Vite类型声明
#       ├── api/
#       │   └── index.ts        # API请求封装
#       ├── pages/
#       │   ├── FlowRanking.tsx      # 资金流向排行页
#       │   ├── ResonanceSignal.tsx  # 共振信号页
#       │   └── TrendAnalysis.tsx    # 趋势分析页
#       └── theme/
#           └── echarts.ts           # ECharts深色主题配置
#
# 注意：项目空间中有些文件同时存在于根目录和src/下（历史遗留），
#       脚本优先使用src/下的版本（Vite标准结构），根目录的同名文件忽略。
#
# 幂等逻辑：如果BUILD_DIR下已有14个核心源文件，跳过下载。
# ==============================================================================
download_code() {
    local force=false
    [ "${1}" = "--force" ] && force=true

    log_step "========== 阶段2：下载源码（幂等）=========="

    mkdir -p "${BUILD_DIR}"
    cd "${BUILD_DIR}"

    # --- 2.0 幂等检测 ---
    if [ "${force}" = false ] && [ -f "package.json" ] && [ -f "vite.config.ts" ]; then
        local src_count=$(find src -type f \( -name "*.tsx" -o -name "*.ts" -o -name "*.css" \) 2>/dev/null | wc -l)
        local expected_src=11
        if [ "${src_count}" -ge "${expected_src}" ]; then
            log_skip "源码已完整（${src_count}个源文件），跳过下载。如需强制重新下载，使用 --code --force"
            return
        else
            log_warn "源文件数不匹配（${src_count}/${expected_src}），重新下载..."
        fi
    fi

    # --- 2.1 创建目录结构 ---
    mkdir -p src/{api,pages,theme}

    # --- 2.2 文件映射表 ---
    # 格式："本地相对路径|项目空间路径"
    # 注意：项目空间中有重复文件（根目录和src/下各一份），这里统一用正确路径
    # ★★★ 新增前端文件后必须在这里补条目！★★★
    local FILES=(
        # ---- 配置文件（必须在根目录）----
        "package.json|/ths-crawler-ui/package.json"
        "index.html|/ths-crawler-ui/index.html"
        "tsconfig.json|/ths-crawler-ui/tsconfig.json"
        "tsconfig.node.json|/ths-crawler-ui/tsconfig.node.json"
        "vite.config.ts|/ths-crawler-ui/vite.config.ts"
        # ---- src/ 入口文件 ----
        "src/App.css|/ths-crawler-ui/src/App.css"
        "src/App.tsx|/ths-crawler-ui/src/App.tsx"
        "src/main.tsx|/ths-crawler-ui/src/main.tsx"
        "src/vite-env.d.ts|/ths-crawler-ui/src/vite-env.d.ts"
        # ---- src/api/ API封装 ----
        "src/api/index.ts|/ths-crawler-ui/src/api/index.ts"
        # ---- src/pages/ 页面组件 ----
        "src/pages/FlowRanking.tsx|/ths-crawler-ui/src/pages/FlowRanking.tsx"
        "src/pages/ResonanceSignal.tsx|/ths-crawler-ui/src/pages/ResonanceSignal.tsx"
        "src/pages/TrendAnalysis.tsx|/ths-crawler-ui/src/pages/TrendAnalysis.tsx"
        # ---- src/theme/ 主题配置 ----
        "src/theme/echarts.ts|/ths-crawler-ui/src/theme/echarts.ts"
    )

    # --- 2.3 批量下载 ---
    # 关键：coze agent file download 把文件保存到【当前目录】的basename，
    # 不保留目录结构。所以必须cd到目标文件所在目录再执行download。
    # stdout是JSON状态信息（不是文件内容），不能重定向到目标文件。
    local ok=0
    local skip=0
    local fail=0
    for entry in "${FILES[@]}"; do
        local_path="${entry%%|*}"
        remote_path="${entry##*|}"

        # 幂等：本地已存在且非空且内容合法，跳过
        # 注意：JSON配置文件以{开头是合法的，只有包含"ok":true的才是CLI状态信息
        if [ -s "${local_path}" ] && ! head -1 "${local_path}" | grep -q '"ok"' 2>/dev/null; then
            skip=$((skip + 1))
            continue
        fi

        # cd到目标文件所在目录
        local target_dir=$(dirname "${local_path}")
        mkdir -p "${target_dir}"

        if (cd "${target_dir}" && coze agent file download \
            --project-id "${PROJECT_ID}" \
            --project-file-path "${remote_path}" 2>/dev/null); then
            if [ -s "${local_path}" ] && ! head -1 "${local_path}" | grep -q '"ok"' 2>/dev/null; then
                ok=$((ok + 1))
            else
                log_error "下载内容异常（可能是JSON状态而非文件内容）: ${remote_path}"
                fail=$((fail + 1))
            fi
        else
            log_error "下载失败: ${remote_path}"
            fail=$((fail + 1))
        fi
    done

    log_info "下载结果: ${ok} 新增, ${skip} 跳过, ${fail} 失败"

    # --- 2.4 清单校验（铁律第2条）---
    local src_count=$(find src -type f \( -name "*.tsx" -o -name "*.ts" -o -name "*.css" \) 2>/dev/null | wc -l)
    local config_count=0
    [ -f "package.json" ] && config_count=$((config_count + 1))
    [ -f "vite.config.ts" ] && config_count=$((config_count + 1))
    [ -f "index.html" ] && config_count=$((config_count + 1))
    [ -f "tsconfig.json" ] && config_count=$((config_count + 1))
    [ -f "tsconfig.node.json" ] && config_count=$((config_count + 1))

    log_info "源文件: ${src_count}个, 配置文件: ${config_count}个"

    if [ "${fail}" -gt 0 ]; then
        log_warn "有${fail}个文件下载失败，npm install可能会报错"
    fi

    state_done "code_download"
    log_step "========== 阶段2完成：源码就绪 =========="
}

# ==============================================================================
# 阶段3：npm install
# ==============================================================================
# 幂等逻辑：如果node_modules已存在且包数量>50，跳过安装。
# npm install在沙箱中耗时约2-5分钟（取决于网络和缓存）。
# 使用淘宝镜像加速。
# ==============================================================================
install_deps() {
    log_step "========== 阶段3：安装依赖（幂等）=========="

    cd "${BUILD_DIR}"

    # --- 3.0 幂等检测 ---
    if [ -d "node_modules" ]; then
        local mod_count=$(ls node_modules 2>/dev/null | wc -l)
        if [ "${mod_count}" -gt 50 ]; then
            log_skip "node_modules已存在（${mod_count}个包），跳过安装。如需重装，删除node_modules后执行"
            return
        else
            log_warn "node_modules不完整（仅${mod_count}个包），重新安装..."
        fi
    fi

    # --- 3.1 设置npm镜像 ---
    log_info "配置npm镜像: ${NPM_REGISTRY}"
    npm config set registry "${NPM_REGISTRY}" 2>/dev/null || true

    # --- 3.2 npm install ---
    # --legacy-peer-deps: 避免peer dependency冲突导致安装失败
    # --prefer-offline: 优先用缓存，沙箱重启后如果npm cache还在能加速
    log_info "开始npm install（首次可能需要2-5分钟，请耐心等待）..."
    npm install --legacy-peer-deps --prefer-offline 2>&1 | tail -10

    # --- 3.3 验证安装结果 ---
    if [ -d "node_modules" ]; then
        local mod_count=$(ls node_modules 2>/dev/null | wc -l)
        # 检查关键依赖是否装好
        local react_ok=false antd_ok=false vite_ok=false
        [ -d "node_modules/react" ] && react_ok=true
        [ -d "node_modules/antd" ] && antd_ok=true
        [ -d "node_modules/vite" ] && vite_ok=true

        if [ "${react_ok}" = true ] && [ "${antd_ok}" = true ] && [ "${vite_ok}" = true ]; then
            log_info "npm install完成！${mod_count}个包，核心依赖(react/antd/vite)均已就位"
        else
            log_warn "npm install完成但有核心依赖缺失: react=${react_ok} antd=${antd_ok} vite=${vite_ok}"
        fi
    else
        log_error "npm install失败！node_modules不存在"
        return 1
    fi

    state_done "npm_install"
    log_step "========== 阶段3完成：依赖就绪 =========="
}

# ==============================================================================
# 阶段4：TypeScript编译检查（可选，不阻塞启动）
# ==============================================================================
# Vite dev server不要求预编译，但tsc能提前发现类型错误。
# 这里做一次轻量检查，失败只告警不中断。
# ==============================================================================
type_check() {
    log_step "========== 阶段4：TypeScript类型检查（可选）=========="

    cd "${BUILD_DIR}"

    if ! command -v npx &>/dev/null; then
        log_warn "npx不可用，跳过类型检查"
        return
    fi

    log_info "执行 tsc --noEmit ..."
    if npx tsc --noEmit 2>&1; then
        log_info "TypeScript类型检查通过"
    else
        log_warn "TypeScript类型检查有错误（不影响dev server启动，但建议修复）"
    fi
}

# ==============================================================================
# 阶段5：启动Vite dev server
# ==============================================================================
# 幂等逻辑：如果dev server已在运行（PID有效或端口有响应），跳过启动。
# Vite dev server默认端口5173，自动代理/api到后端8100。
# ==============================================================================
start_dev() {
    log_step "========== 阶段5：启动dev server（幂等）=========="

    cd "${BUILD_DIR}"

    # --- 5.0 幂等检测 ---
    if [ -f "${UI_PID_FILE}" ] && kill -0 "$(cat ${UI_PID_FILE})" 2>/dev/null; then
        log_skip "dev server已在运行(PID=$(cat ${UI_PID_FILE}), 端口=${UI_PORT})"
        return
    fi

    # 通过端口判断
    if curl -s "http://localhost:${UI_PORT}" 2>/dev/null | grep -q "."; then
        log_skip "检测到端口${UI_PORT}有服务在响应，跳过启动"
        return
    fi

    # --- 5.1 清理旧进程 ---
    if [ -f "${UI_PID_FILE}" ]; then
        local old_pid=$(cat "${UI_PID_FILE}")
        kill "${old_pid}" 2>/dev/null || true
        rm -f "${UI_PID_FILE}"
    fi

    # 检查端口占用
    if ss -tlnp 2>/dev/null | grep -q ":${UI_PORT}"; then
        log_warn "端口${UI_PORT}被占用，尝试释放..."
        fuser -k "${UI_PORT}/tcp" 2>/dev/null || true
        sleep 2
    fi

    # --- 5.2 检查后端状态 ---
    # 前端虽然可以独立启动，但API代理依赖后端
    if curl -s "http://localhost:${BACKEND_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "."; then
        log_info "后端${BACKEND_PORT}端口有响应，API代理将正常工作"
    else
        log_warn "后端${BACKEND_PORT}端口无响应！前端可启动但API数据不可用"
        log_warn "请先执行后端脚本: bash sandbox-bootstrap.sh --start"
    fi

    # --- 5.3 启动Vite dev server ---
    # --host 0.0.0.0: 允许非localhost访问（沙箱可能有端口转发场景）
    log_info "启动Vite dev server..."
    nohup npx vite --host 0.0.0.0 > vite.log 2>&1 &
    local pid=$!
    echo "${pid}" > "${UI_PID_FILE}"

    # --- 5.4 等待启动 ---
    log_info "等待dev server启动（最多30秒）..."
    local started=false
    for i in $(seq 1 30); do
        if curl -s "http://localhost:${UI_PORT}" 2>/dev/null | grep -q "."; then
            started=true
            break
        fi
        if grep -q "ready in" vite.log 2>/dev/null; then
            started=true
            break
        fi
        sleep 1
    done

    if [ "${started}" = true ]; then
        log_info "dev server启动成功！PID=${pid}，端口=${UI_PORT}"
        log_info "访问地址: http://localhost:${UI_PORT}"
    else
        log_error "dev server启动超时！查看日志："
        tail -20 vite.log
        return 1
    fi

    state_done "dev_started"
    log_step "========== 阶段5完成：前端运行中 =========="
}

# ==============================================================================
# 阶段6：验证前端页面可访问
# ==============================================================================
verify_frontend() {
    log_step "========== 阶段6：前端验证 =========="

    local BASE="http://localhost:${UI_PORT}"

    # --- 6.1 首页HTML ---
    log_info "检查首页..."
    local html=$(curl -s "${BASE}/" 2>/dev/null)
    if echo "${html}" | grep -q "root"; then
        log_info "✓ 首页HTML正常（包含root挂载点）"
    else
        log_warn "✗ 首页HTML异常"
    fi

    # --- 6.2 Vite HMR WebSocket ---
    log_info "检查Vite资源..."
    if curl -s -o /dev/null -w "%{http_code}" "${BASE}/@vite/client" 2>/dev/null | grep -q "200"; then
        log_info "✓ Vite HMR客户端正常"
    else
        log_warn "✗ Vite HMR客户端异常"
    fi

    # --- 6.3 后端API代理 ---
    log_info "检查API代理..."
    local api_status=$(curl -s -o /dev/null -w "%{http_code}" "${BASE}/api/industry-flow/latest?topN=1" 2>/dev/null)
    if [ "${api_status}" = "200" ]; then
        log_info "✓ API代理正常（后端返回200）"
    elif [ "${api_status}" = "502" ] || [ "${api_status}" = "504" ]; then
        log_warn "✗ API代理返回${api_status}（后端未启动？）"
    else
        log_warn "△ API代理返回${api_status}（后端可能未启动或无数据）"
    fi

    # --- 6.4 TypeScript源文件加载 ---
    log_info "检查TSX文件加载..."
    if curl -s -o /dev/null -w "%{http_code}" "${BASE}/src/App.tsx" 2>/dev/null | grep -q "200"; then
        log_info "✓ TSX文件Vite转换正常"
    fi

    echo ""
    log_info "===== 前端验证总结 ====="
    log_info "前端地址: http://localhost:${UI_PORT}"
    log_info "API代理: http://localhost:${UI_PORT}/api → http://localhost:${BACKEND_PORT}/api"
    log_info "如需查看完整页面，请在浏览器打开上述地址"

    log_step "========== 阶段6完成：前端验证完毕 =========="
}

# ==============================================================================
# 停止dev server
# ==============================================================================
stop_dev() {
    if [ -f "${UI_PID_FILE}" ]; then
        local pid=$(cat "${UI_PID_FILE}")
        if kill -0 "${pid}" 2>/dev/null; then
            # Vite进程可能有子进程，先发SIGTERM，等2秒再发SIGKILL
            kill "${pid}" 2>/dev/null || true
            sleep 2
            kill -9 "${pid}" 2>/dev/null || true
            log_info "dev server已停止(PID=${pid})"
        fi
        rm -f "${UI_PID_FILE}"
    else
        fuser -k "${UI_PORT}/tcp" 2>/dev/null && log_info "端口${UI_PORT}进程已终止" || log_info "无运行中的dev server"
    fi
    state_reset "dev_started"
}

# ==============================================================================
# 重置状态标记
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
    --code)
        download_code "${2}"
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
        download_code
        install_deps
        start_dev
        verify_frontend
        ;;
    *)
        echo "用法: bash ui-bootstrap.sh [选项]"
        echo ""
        echo "选项:"
        echo "  (无参数)    完整搭建（环境+代码+依赖+启动+验证）"
        echo "  --env       仅安装Node.js环境"
        echo "  --code      仅下载源码（加--force强制重新下载）"
        echo "  --install   仅npm install"
        echo "  --start     仅启动Vite dev server"
        echo "  --verify    仅验证前端页面"
        echo "  --stop      停止dev server"
        echo "  --status    查看当前状态"
        echo "  --reset     清除状态标记（下次全量执行）"
        echo ""
        echo "幂等性说明："
        echo "  每一步都检测当前状态，已满足则跳过。"
        echo "  Node.js已装则跳过安装，node_modules已存在则跳过npm install。"
        echo ""
        echo "依赖关系："
        echo "  前端API代理需要后端Spring Boot在${BACKEND_PORT}端口运行。"
        echo "  如后端未启动，前端页面可打开但无数据。"
        echo "  建议先执行: bash sandbox-bootstrap.sh（后端一键启动）"
        exit 1
        ;;
esac
