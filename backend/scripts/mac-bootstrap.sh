#!/bin/bash
# ==============================================================================
# ths-crawler 后端 Mac本地启动脚本（幂等版）
# ==============================================================================
# 诞生背景：
#   2026-06-18，ths-crawler项目之前只写了云沙箱版的sandbox-bootstrap.sh，
#   用户在Mac M1本地开发时每次也要手动启动MySQL/Redis/编译/启动，操作繁琐。
#   Mac本地比沙箱简单得多：brew管依赖、本地有完整源码、不需要coze CLI下载。
#   特仿照沙箱版写Mac版，保持幂等设计，一条命令搞定。
#
# 与沙箱版的核心差异：
#   1. 用brew安装/检测依赖（而非apt-get）
#   2. 源码直接读本地目录（不需要coze CLI download）
#   3. MySQL/Redis用brew services管理（而非service命令）
#   4. M1芯片Homebrew路径: /opt/homebrew（而非/usr/local）
#   5. 不上传产物到项目空间（本地开发，编译即用）
#
# 使用方式：
#   bash mac-bootstrap.sh              # 完整流程（环境+编译+启动+数据+验证）
#   bash mac-bootstrap.sh --env        # 仅检查/安装环境依赖
#   bash mac-bootstrap.sh --build      # 仅编译打包
#   bash mac-bootstrap.sh --start      # 仅启动Spring Boot
#   bash mac-bootstrap.sh --data       # 仅灌入测试数据
#   bash mac-bootstrap.sh --verify     # 仅API冒烟测试
#   bash mac-bootstrap.sh --stop       # 停止应用
#   bash mac-bootstrap.sh --status     # 查看当前环境状态
#
# 前置条件：
#   1. macOS + Homebrew已安装
#   2. 项目源码在 PROJECT_DIR 指定的本地目录下
# ==============================================================================

set -e

# ===== 全局配置 =====
# 项目源码目录（按实际路径修改）
PROJECT_DIR="${HOME}/Projects/ths-crawler"
# Spring Boot端口
APP_PORT=8100
# MySQL配置（Mac brew版默认root无密码，按实际修改）
MYSQL_USER="root"
MYSQL_PASS=""
MYSQL_DB="ths_crawler"
# PID文件
APP_PID_FILE="/tmp/ths-crawler-app.pid"
# 状态标记目录
STATE_DIR="/tmp/ths-crawler/.state"

# ===== M1芯片Homebrew路径 =====
# Intel Mac: /usr/local/bin/brew
# M1 Mac: /opt/homebrew/bin/brew
# 自动检测，优先M1路径
if [ -x "/opt/homebrew/bin/brew" ]; then
    BREW_BIN="/opt/homebrew/bin/brew"
    HOMEBREW_PREFIX="/opt/homebrew"
elif [ -x "/usr/local/bin/brew" ]; then
    BREW_BIN="/usr/local/bin/brew"
    HOMEBREW_PREFIX="/usr/local"
else
    BREW_BIN="brew"
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
    echo "====== ths-crawler Mac本地环境状态 ======"
    echo ""

    # Homebrew
    if command -v brew &>/dev/null; then
        echo -e "  Homebrew:  ${GREEN}✓ $(brew --version 2>/dev/null | head -1)${NC}"
    else
        echo -e "  Homebrew:  ${RED}✗ 未安装${NC}"
    fi

    # JDK
    if command -v java &>/dev/null; then
        echo -e "  JDK:       ${GREEN}✓ $(java -version 2>&1 | head -1)${NC}"
    else
        echo -e "  JDK:       ${RED}✗ 未安装${NC}"
    fi

    # Maven
    if command -v mvn &>/dev/null; then
        echo -e "  Maven:     ${GREEN}✓ $(mvn -version 2>&1 | head -1 | grep -oP '\d+\.\d+\.\d+')${NC}"
    else
        echo -e "  Maven:     ${RED}✗ 未安装${NC}"
    fi

    # MySQL
    if brew services list 2>/dev/null | grep -q "mysql.*started"; then
        local db_exists=$(mysql -u "${MYSQL_USER}" ${MYSQL_PASS:+-p"${MYSQL_PASS}"} -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${MYSQL_DB}'" 2>/dev/null)
        if [ -n "${db_exists}" ]; then
            local row_count=$(mysql -u "${MYSQL_USER}" ${MYSQL_PASS:+-p"${MYSQL_PASS}"} -N -e "SELECT COUNT(*) FROM industry_capital_flow" "${MYSQL_DB}" 2>/dev/null || echo "0")
            echo -e "  MySQL:     ${GREEN}✓ 运行中，库=${MYSQL_DB}，数据=${row_count}条${NC}"
        else
            echo -e "  MySQL:     ${GREEN}✓ 运行中，库=${MYSQL_DB}未创建${NC}"
        fi
    elif brew services list 2>/dev/null | grep -q "mysql"; then
        echo -e "  MySQL:     ${YELLOW}△ 已安装但未运行${NC}"
    else
        echo -e "  MySQL:     ${RED}✗ 未安装${NC}"
    fi

    # Redis
    if redis-cli ping 2>/dev/null | grep -q PONG; then
        echo -e "  Redis:     ${GREEN}✓ 运行中${NC}"
    elif brew services list 2>/dev/null | grep -q "redis"; then
        echo -e "  Redis:     ${YELLOW}△ 已安装但未运行${NC}"
    else
        echo -e "  Redis:     ${RED}✗ 未安装${NC}"
    fi

    # 源码
    if [ -d "${PROJECT_DIR}/src" ]; then
        local java_count=$(find "${PROJECT_DIR}/src" -name "*.java" 2>/dev/null | wc -l)
        echo -e "  源码:      ${GREEN}✓ ${java_count}个Java文件 (${PROJECT_DIR})${NC}"
    else
        echo -e "  源码:      ${RED}✗ ${PROJECT_DIR}/src 不存在${NC}"
    fi

    # 编译产物
    if ls ${PROJECT_DIR}/target/*.jar 2>/dev/null | head -1 | grep -q .; then
        local jar_path=$(ls ${PROJECT_DIR}/target/*.jar 2>/dev/null | head -1)
        local jar_size=$(du -h "${jar_path}" 2>/dev/null | cut -f1)
        echo -e "  编译产物:  ${GREEN}✓ $(basename ${jar_path}) (${jar_size})${NC}"
    else
        echo -e "  编译产物:  ${RED}✗ 未编译${NC}"
    fi

    # 应用
    if [ -f "${APP_PID_FILE}" ] && kill -0 "$(cat ${APP_PID_FILE})" 2>/dev/null; then
        echo -e "  应用:      ${GREEN}✓ 运行中 PID=$(cat ${APP_PID_FILE}) 端口=${APP_PORT}${NC}"
    else
        echo -e "  应用:      ${YELLOW}△ 未运行${NC}"
    fi

    echo ""
    echo "========================================"
}

# ==============================================================================
# 阶段1：环境搭建（brew安装JDK/Maven/MySQL/Redis）
# ==============================================================================
# Mac用brew，比沙箱apt-get优雅得多：
#   - brew install xxx：不存在才装，已装就跳过（自带幂等）
#   - brew services start xxx：已启动则跳过
#   - 不需要清理apt锁
#
# Mac M1特殊处理：
#   - Homebrew路径 /opt/homebrew（非/usr/local）
#   - brew install openjdk 后需要手动link
#   - MySQL 8.x默认root无密码（auth_socket或空密码）
# ==============================================================================
setup_environment() {
    log_step "========== 阶段1：环境搭建（幂等）=========="

    # --- 1.0 Homebrew自检 ---
    if ! command -v brew &>/dev/null; then
        log_error "Homebrew未安装！请先执行: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        return 1
    fi
    log_info "Homebrew: $(brew --version 2>/dev/null | head -1)"

    # --- 1.1 JDK ---
    # Mac brew openjdk是最新版，项目要求Java 17+（Spring Boot 3.x）
    # 检测：java命令存在且版本≥17
    if java -version 2>&1 | grep -qE "version \"(1[7-9]|[2-9][0-9])"; then
        log_skip "JDK $(java -version 2>&1 | head -1) 已安装"
    else
        log_info "安装OpenJDK..."
        brew install openjdk 2>&1 | tail -5
        # brew openjdk需要手动link
        if [ -d "${HOMEBREW_PREFIX}/opt/openjdk" ]; then
            sudo ln -sfn "${HOMEBREW_PREFIX}/opt/openjdk/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk.jdk 2>/dev/null || true
        fi
        # 刷新PATH
        export PATH="${HOMEBREW_PREFIX}/opt/openjdk/bin:${PATH}"
        log_info "JDK 安装完成: $(java -version 2>&1 | head -1)"
    fi

    # --- 1.2 Maven ---
    if command -v mvn &>/dev/null; then
        log_skip "Maven $(mvn -version 2>&1 | head -1 | grep -oP '\d+\.\d+\.\d+') 已安装"
    else
        log_info "安装Maven..."
        brew install maven 2>&1 | tail -5
        log_info "Maven 安装完成"
    fi

    # --- 1.3 MySQL ---
    # Mac brew版MySQL：brew install mysql → brew services start mysql
    # 首次启动后会生成临时密码（8.x）或root无密码（旧版）
    if brew list mysql &>/dev/null 2>&1; then
        log_skip "MySQL 已安装"
    else
        log_info "安装MySQL..."
        brew install mysql 2>&1 | tail -5
        log_info "MySQL 安装完成"
    fi

    # 启动MySQL
    if brew services list 2>/dev/null | grep -q "mysql.*started"; then
        log_skip "MySQL 已在运行"
    else
        log_info "启动MySQL..."
        brew services start mysql 2>&1
        # 等待就绪
        for i in $(seq 1 15); do
            if mysql -u root -e "SELECT 1" &>/dev/null; then
                break
            fi
            # 尝试带密码连接
            if [ -n "${MYSQL_PASS}" ] && mysql -u root -p"${MYSQL_PASS}" -e "SELECT 1" &>/dev/null; then
                break
            fi
            sleep 1
        done
        log_info "MySQL 启动完成"
    fi

    # --- 1.4 Redis ---
    if brew list redis &>/dev/null 2>&1; then
        log_skip "Redis 已安装"
    else
        log_info "安装Redis..."
        brew install redis 2>&1 | tail -5
        log_info "Redis 安装完成"
    fi

    # 启动Redis
    if redis-cli ping 2>/dev/null | grep -q PONG; then
        log_skip "Redis 已在运行"
    else
        log_info "启动Redis..."
        brew services start redis 2>&1
        for i in $(seq 1 10); do
            if redis-cli ping 2>/dev/null | grep -q PONG; then
                break
            fi
            sleep 1
        done
        log_info "Redis 启动完成"
    fi

    # --- 1.5 创建数据库和表 ---
    # 构建mysql命令（处理密码为空的情况）
    local MYSQL_CMD="mysql -u ${MYSQL_USER}"
    [ -n "${MYSQL_PASS}" ] && MYSQL_CMD="${MYSQL_CMD} -p${MYSQL_PASS}"

    local db_exists=$(${MYSQL_CMD} -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${MYSQL_DB}'" 2>/dev/null)
    if [ -n "${db_exists}" ]; then
        local table_count=$(${MYSQL_CMD} -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${MYSQL_DB}'" 2>/dev/null)
        if [ "${table_count}" -ge 4 ]; then
            log_skip "数据库 ${MYSQL_DB} 及核心表已存在"
        else
            _create_db_and_tables
        fi
    else
        _create_db_and_tables
    fi

    state_done "env_setup"
    log_step "========== 阶段1完成：环境就绪 =========="
}

_create_db_and_tables() {
    local MYSQL_CMD="mysql -u ${MYSQL_USER}"
    [ -n "${MYSQL_PASS}" ] && MYSQL_CMD="${MYSQL_CMD} -p${MYSQL_PASS}"

    log_info "创建数据库 ${MYSQL_DB} 及表..."
    ${MYSQL_CMD} -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} DEFAULT CHARSET utf8mb4;" 2>/dev/null

    ${MYSQL_CMD} "${MYSQL_DB}" << 'EOSQL'
CREATE TABLE IF NOT EXISTS industry_capital_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    industry_name VARCHAR(50) NOT NULL COMMENT '行业名称',
    industry_code VARCHAR(20) COMMENT '行业代码',
    trade_date DATE NOT NULL COMMENT '交易日期',
    change_pct DECIMAL(10,2) COMMENT '涨跌幅(%)',
    main_net_amount DECIMAL(18,2) COMMENT '主力净流入(万元)',
    main_net_pct DECIMAL(10,2) COMMENT '主力净占比(%)',
    super_net_amount DECIMAL(18,2) COMMENT '超大单净流入(万元)',
    super_net_pct DECIMAL(10,2) COMMENT '超大单净占比(%)',
    big_net_amount DECIMAL(18,2) COMMENT '大单净流入(万元)',
    big_net_pct DECIMAL(10,2) COMMENT '大单净占比(%)',
    mid_net_amount DECIMAL(18,2) COMMENT '中单净流入(万元)',
    mid_net_pct DECIMAL(10,2) COMMENT '中单净占比(%)',
    small_net_amount DECIMAL(18,2) COMMENT '小单净流入(万元)',
    small_net_pct DECIMAL(10,2) COMMENT '小单净占比(%)',
    fetch_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_date (industry_name, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS industry_trend_stat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    industry_name VARCHAR(50) NOT NULL,
    trade_date DATE NOT NULL,
    period INT NOT NULL COMMENT '计算周期',
    slope DECIMAL(18,4) COMMENT '线性回归斜率',
    r_squared DECIMAL(10,6) COMMENT 'R²决定系数',
    avg_net_amount DECIMAL(18,2) COMMENT '期间均值',
    trend_direction VARCHAR(10) COMMENT '趋势方向',
    resonance_type VARCHAR(20) COMMENT '共振类型',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_date_period (industry_name, trade_date, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS crawl_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    crawl_type VARCHAR(50) NOT NULL,
    crawl_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    record_count INT DEFAULT 0,
    error_message TEXT,
    start_time DATETIME,
    end_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sector_capital_flow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sector_name VARCHAR(50) NOT NULL,
    sector_code VARCHAR(20),
    sector_type VARCHAR(20) COMMENT '行业/概念',
    trade_date DATE NOT NULL,
    change_pct DECIMAL(10,2),
    main_net_amount DECIMAL(18,2),
    main_net_pct DECIMAL(10,2),
    super_net_amount DECIMAL(18,2),
    super_net_pct DECIMAL(10,2),
    big_net_amount DECIMAL(18,2),
    big_net_pct DECIMAL(10,2),
    mid_net_amount DECIMAL(18,2),
    mid_net_pct DECIMAL(10,2),
    small_net_amount DECIMAL(18,2),
    small_net_pct DECIMAL(10,2),
    fetch_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sector_date (sector_name, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
EOSQL
    log_info "数据库和表创建完成"
}

# ==============================================================================
# 阶段2：Maven编译打包
# ==============================================================================
# Mac本地直接编译项目源码目录，不需要从项目空间下载。
# 幂等逻辑：如果target下jar比所有Java源文件新，跳过编译。
# ==============================================================================
build_project() {
    log_step "========== 阶段2：编译打包（幂等）=========="

    # 检查源码目录
    if [ ! -d "${PROJECT_DIR}/src" ]; then
        log_error "项目源码目录不存在: ${PROJECT_DIR}/src"
        log_error "请确认PROJECT_DIR配置是否正确，或先clone项目到本地"
        return 1
    fi

    cd "${PROJECT_DIR}"

    # --- 2.0 幂等检测 ---
    if ls target/*.jar 2>/dev/null | head -1 | grep -q .; then
        local jar_path=$(ls target/*.jar 2>/dev/null | head -1)
        local jar_newer=true
        for src_file in $(find src -name "*.java" -newer "${jar_path}" 2>/dev/null | head -1); do
            jar_newer=false
            break
        done
        if [ "${jar_newer}" = true ]; then
            local jar_size=$(du -h "${jar_path}" | cut -f1)
            log_skip "编译产物已存在且比源码新: $(basename ${jar_path}) (${jar_size})。如需重编，删除target/后再执行"
            return
        else
            log_info "源码有更新，重新编译..."
        fi
    fi

    # --- 2.1 Maven编译 ---
    log_info "开始Maven编译（首次会下载依赖，约1-3分钟）..."
    mvn clean package -DskipTests -q 2>&1
    local mvn_exit=$?

    if [ ${mvn_exit} -eq 0 ] && ls target/*.jar 2>/dev/null | head -1 | grep -q .; then
        local jar_name=$(ls target/*.jar | head -1)
        local jar_size=$(du -h "${jar_name}" | cut -f1)
        log_info "编译成功！JAR: ${jar_name} (${jar_size})"
    else
        log_error "编译失败！"
        mvn clean package -DskipTests 2>&1 | tail -30
        return 1
    fi

    state_done "build"
    log_step "========== 阶段2完成：编译就绪 =========="
}

# ==============================================================================
# 阶段3：启动Spring Boot
# ==============================================================================
start_app() {
    log_step "========== 阶段3：启动应用（幂等）=========="

    cd "${PROJECT_DIR}"

    # --- 3.0 幂等检测 ---
    if [ -f "${APP_PID_FILE}" ] && kill -0 "$(cat ${APP_PID_FILE})" 2>/dev/null; then
        log_skip "应用已在运行(PID=$(cat ${APP_PID_FILE}), 端口=${APP_PORT})"
        return
    fi

    if curl -s "http://localhost:${APP_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "."; then
        log_skip "检测到端口${APP_PORT}有应用在响应，跳过启动"
        return
    fi

    # --- 3.1 清理旧进程 ---
    if [ -f "${APP_PID_FILE}" ]; then
        local old_pid=$(cat "${APP_PID_FILE}")
        kill "${old_pid}" 2>/dev/null || true
        rm -f "${APP_PID_FILE}"
    fi

    if lsof -ti:${APP_PORT} 2>/dev/null | grep -q .; then
        log_warn "端口${APP_PORT}被占用，尝试释放..."
        lsof -ti:${APP_PORT} | xargs kill 2>/dev/null || true
        sleep 2
    fi

    # --- 3.2 构建启动参数 ---
    local jar_path=$(ls target/*.jar | head -1)
    local spring_opts=""
    # 如果配置了MySQL密码，传入；否则Spring Boot读application.yml默认值
    [ -n "${MYSQL_PASS}" ] && spring_opts="${spring_opts} --spring.datasource.password=${MYSQL_PASS}"

    log_info "启动Spring Boot: ${jar_path}"
    java -jar "${jar_path}" ${spring_opts} > /tmp/ths-crawler-app.log 2>&1 &
    local pid=$!
    echo "${pid}" > "${APP_PID_FILE}"

    # --- 3.3 等待启动 ---
    log_info "等待应用启动（最多60秒）..."
    local started=false
    for i in $(seq 1 60); do
        if curl -s "http://localhost:${APP_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "."; then
            started=true
            break
        fi
        if grep -q "Started ThsCrawlerApplication" /tmp/ths-crawler-app.log 2>/dev/null; then
            started=true
            break
        fi
        sleep 1
    done

    if [ "${started}" = true ]; then
        log_info "应用启动成功！PID=${pid}，端口=${APP_PORT}"
        log_info "API地址: http://localhost:${APP_PORT}"
    else
        log_error "应用启动超时！查看日志: tail -50 /tmp/ths-crawler-app.log"
        return 1
    fi

    state_done "app_started"
    log_step "========== 阶段3完成：应用运行中 =========="
}

# ==============================================================================
# 阶段4：灌入测试数据
# ==============================================================================
insert_test_data() {
    log_step "========== 阶段4：灌入测试数据（幂等）=========="

    local MYSQL_CMD="mysql -u ${MYSQL_USER}"
    [ -n "${MYSQL_PASS}" ] && MYSQL_CMD="${MYSQL_CMD} -p${MYSQL_PASS}"

    # 检查是否已有数据
    local existing=$(${MYSQL_CMD} -N -e "SELECT COUNT(*) FROM industry_capital_flow" "${MYSQL_DB}" 2>/dev/null)
    if [ "${existing}" -gt 0 ]; then
        log_skip "已有${existing}条数据，跳过灌入。如需重灌，先: ${MYSQL_CMD} -e \"TRUNCATE industry_capital_flow\" ${MYSQL_DB}"
        return
    fi

    log_info "生成测试数据SQL（40行业 × 10交易日 = 400条）..."

    cat > /tmp/ths_test_data.sql << 'SQLDATA'
-- 测试数据：40行业 × 10交易日 = 400条
-- 分4组趋势：强势流入 / 温和流入 / 资金流出 / 强流出

DROP PROCEDURE IF EXISTS generate_test_data;
DELIMITER //
CREATE PROCEDURE generate_test_data()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE dt DATE;
    DECLARE dates_array VARCHAR(200) DEFAULT '2026-06-03,2026-06-04,2026-06-05,2026-06-06,2026-06-09,2026-06-10,2026-06-11,2026-06-12,2026-06-13,2026-06-16';
    
    -- 强势流入组（10行业）
    WHILE i < 10 DO
        SET dt = STR_TO_DATE(SUBSTRING_INDEX(SUBSTRING_INDEX(dates_array, ',', i+1), ',', -1), '%Y-%m-%d');
        INSERT IGNORE INTO industry_capital_flow (industry_name, industry_code, trade_date, change_pct, main_net_amount, main_net_pct, super_net_amount, super_net_pct, big_net_amount, big_net_pct, mid_net_amount, mid_net_pct, small_net_amount, small_net_pct)
        VALUES
        ('半导体','881121',dt, 2.5+i*0.1, 80000+i*1200, 15.0, 50000+i*800, 10.0, 30000+i*400, 5.0, -20000-i*300, -3.5, -15000-i*200, -2.8),
        ('电池','881281',dt, 1.8+i*0.08, 75000+i*1100, 13.5, 45000+i*700, 9.5, 30000+i*400, 4.0, -18000-i*250, -3.0, -12000-i*180, -2.2),
        ('消费电子','881102',dt, 2.2+i*0.09, 82000+i*1300, 14.8, 52000+i*850, 10.2, 30000+i*450, 4.6, -22000-i*350, -3.8, -16000-i*220, -3.0),
        ('新能源车','881253',dt, 1.9+i*0.07, 78000+i*1050, 13.0, 48000+i*680, 9.0, 30000+i*370, 4.0, -19000-i*280, -3.2, -13000-i*190, -2.5),
        ('光伏','881154',dt, 2.1+i*0.08, 76000+i*1150, 14.2, 47000+i*720, 9.8, 29000+i*430, 4.4, -21000-i*320, -3.6, -14000-i*210, -2.7),
        ('军工','881243',dt, 1.7+i*0.06, 72000+i*980, 12.5, 44000+i*650, 8.8, 28000+i*330, 3.7, -17000-i*260, -2.8, -11000-i*170, -2.0),
        ('通信设备','881233',dt, 2.0+i*0.07, 79000+i*1080, 13.8, 49000+i*700, 9.2, 30000+i*380, 4.6, -20000-i*300, -3.4, -15000-i*200, -2.6),
        ('计算机','881172',dt, 2.3+i*0.1, 85000+i*1400, 15.5, 53000+i*900, 10.8, 32000+i*500, 4.7, -23000-i*380, -4.0, -17000-i*240, -3.2),
        ('电力','881263',dt, 1.5+i*0.05, 68000+i*900, 11.8, 41000+i*580, 8.2, 27000+i*320, 3.6, -16000-i*240, -2.6, -10000-i*150, -1.8),
        ('传媒','881213',dt, 1.6+i*0.06, 70000+i*950, 12.2, 43000+i*620, 8.5, 27000+i*330, 3.7, -16500-i*250, -2.9, -10500-i*160, -1.9);
        SET i = i + 1;
    END WHILE;
    
    -- 温和流入组（10行业）
    SET i = 0;
    WHILE i < 10 DO
        SET dt = STR_TO_DATE(SUBSTRING_INDEX(SUBSTRING_INDEX(dates_array, ',', i+1), ',', -1), '%Y-%m-%d');
        INSERT IGNORE INTO industry_capital_flow (industry_name, industry_code, trade_date, change_pct, main_net_amount, main_net_pct, super_net_amount, super_net_pct, big_net_amount, big_net_pct, mid_net_amount, mid_net_pct, small_net_amount, small_net_pct)
        VALUES
        ('医疗器械','881143',dt, 0.5+i*0.02, 5000+i*80, 5.0, 3000+i*50, 3.0, 2000+i*30, 2.0, -1500-i*25, -1.5, -800-i*15, -0.8),
        ('化学制药','881163',dt, 0.4+i*0.02, 4500+i*70, 4.5, 2800+i*45, 2.8, 1700+i*25, 1.7, -1200-i*20, -1.2, -650-i*12, -0.7),
        ('中药','881153',dt, 0.3+i*0.01, 3800+i*60, 3.8, 2400+i*40, 2.4, 1400+i*20, 1.4, -1000-i*18, -1.0, -550-i*10, -0.6),
        ('零售','881183',dt, 0.6+i*0.03, 5500+i*90, 5.5, 3500+i*55, 3.5, 2000+i*35, 2.0, -1800-i*30, -1.8, -1000-i*18, -1.0),
        ('环保','881273',dt, 0.2+i*0.01, 3200+i*55, 3.2, 2000+i*35, 2.0, 1200+i*20, 1.2, -800-i*15, -0.8, -450-i*8, -0.5),
        ('家电','881193',dt, 0.7+i*0.03, 6000+i*100, 6.0, 3800+i*60, 3.8, 2200+i*40, 2.2, -2000-i*35, -2.0, -1100-i*20, -1.1),
        ('建材','881223',dt, 0.1+i*0.01, 2800+i*45, 2.8, 1800+i*30, 1.8, 1000+i*15, 1.0, -700-i*12, -0.7, -400-i*7, -0.4),
        ('燃气','881262',dt, 0.3+i*0.02, 4200+i*65, 4.2, 2600+i*42, 2.6, 1600+i*23, 1.6, -1100-i*18, -1.1, -600-i*11, -0.6),
        ('钢铁','881173',dt, -0.1+i*0.02, 2500+i*40, 2.5, 1500+i*25, 1.5, 1000+i*15, 1.0, -600-i*10, -0.6, -350-i*6, -0.4),
        ('煤炭','881112',dt, -0.2+i*0.01, 2000+i*35, 2.0, 1200+i*20, 1.2, 800+i*15, 0.8, -500-i*8, -0.5, -280-i*5, -0.3);
        SET i = i + 1;
    END WHILE;
    
    -- 资金流出组（10行业）
    SET i = 0;
    WHILE i < 10 DO
        SET dt = STR_TO_DATE(SUBSTRING_INDEX(SUBSTRING_INDEX(dates_array, ',', i+1), ',', -1), '%Y-%m-%d');
        INSERT IGNORE INTO industry_capital_flow (industry_name, industry_code, trade_date, change_pct, main_net_amount, main_net_pct, super_net_amount, super_net_pct, big_net_amount, big_net_pct, mid_net_amount, mid_net_pct, small_net_amount, small_net_pct)
        VALUES
        ('房地产','881202',dt, -1.2-i*0.05, -25000-i*500, -8.0, -15000-i*300, -5.0, -10000-i*200, -3.0, 8000+i*150, 2.5, 6000+i*120, 2.0),
        ('银行','881192',dt, -0.8-i*0.03, -18000-i*350, -6.5, -11000-i*220, -4.0, -7000-i*130, -2.5, 6000+i*110, 2.0, 4500+i*90, 1.5),
        ('保险','881182',dt, -0.9-i*0.04, -20000-i*400, -7.0, -12000-i*250, -4.5, -8000-i*150, -2.5, 6500+i*120, 2.2, 5000+i*100, 1.7),
        ('证券','881201',dt, -1.0-i*0.04, -22000-i*450, -7.5, -13000-i*270, -4.8, -9000-i*180, -2.7, 7000+i*130, 2.4, 5500+i*110, 1.8),
        ('建筑装饰','881222',dt, -0.6-i*0.03, -15000-i*280, -5.5, -9000-i*170, -3.2, -6000-i*110, -2.3, 5000+i*90, 1.8, 3800+i*75, 1.3),
        ('交通运输','881212',dt, -0.5-i*0.02, -12000-i*220, -4.5, -7000-i*135, -2.5, -5000-i*85, -2.0, 4000+i*70, 1.5, 3000+i*55, 1.0),
        ('汽车整车','881252',dt, -0.7-i*0.03, -16000-i*300, -5.8, -9500-i*180, -3.5, -6500-i*120, -2.3, 5200+i*95, 1.9, 4000+i*80, 1.4),
        ('白色家电','881242',dt, -0.4-i*0.02, -10000-i*180, -3.8, -6000-i*110, -2.2, -4000-i*70, -1.6, 3200+i*60, 1.2, 2500+i*45, 0.9),
        ('农产品加工','881232',dt, -0.3-i*0.01, -8000-i*150, -3.0, -4800-i*90, -1.8, -3200-i*60, -1.2, 2600+i*50, 1.0, 2000+i*35, 0.7),
        ('养殖业','881142',dt, -0.5-i*0.02, -11000-i*200, -4.0, -6500-i*120, -2.4, -4500-i*80, -1.6, 3500+i*65, 1.3, 2800+i*50, 1.0);
        SET i = i + 1;
    END WHILE;
    
    -- 强流出组（10行业）
    SET i = 0;
    WHILE i < 10 DO
        SET dt = STR_TO_DATE(SUBSTRING_INDEX(SUBSTRING_INDEX(dates_array, ',', i+1), ',', -1), '%Y-%m-%d');
        INSERT IGNORE INTO industry_capital_flow (industry_name, industry_code, trade_date, change_pct, main_net_amount, main_net_pct, super_net_amount, super_net_pct, big_net_amount, big_net_pct, mid_net_amount, mid_net_pct, small_net_amount, small_net_pct)
        VALUES
        ('造纸','881272',dt, -2.0-i*0.08, -55000-i*1000, -12.0, -33000-i*600, -7.5, -22000-i*400, -4.5, 18000+i*350, 4.0, 13000+i*250, 3.0),
        ('港口','881261',dt, -1.8-i*0.07, -52000-i*950, -11.5, -31000-i*580, -7.0, -21000-i*370, -4.5, 17000+i*320, 3.8, 12000+i*230, 2.8),
        ('公路铁路','881251',dt, -1.5-i*0.06, -48000-i*880, -10.8, -29000-i*530, -6.5, -19000-i*350, -4.3, 16000+i*300, 3.5, 11000+i*210, 2.5),
        ('物流','881241',dt, -1.9-i*0.07, -53000-i*970, -11.8, -32000-i*590, -7.2, -21000-i*380, -4.6, 17500+i*330, 3.9, 12500+i*240, 2.9),
        ('酒店餐饮','881231',dt, -2.2-i*0.09, -58000-i*1050, -12.8, -35000-i*630, -8.0, -23000-i*420, -4.8, 19000+i*360, 4.2, 14000+i*270, 3.2),
        ('旅游','881221',dt, -2.1-i*0.08, -56000-i*1020, -12.3, -34000-i*610, -7.8, -22000-i*410, -4.5, 18500+i*350, 4.1, 13500+i*260, 3.1),
        ('教育','881211',dt, -1.7-i*0.07, -50000-i*920, -11.2, -30000-i*550, -6.8, -20000-i*370, -4.4, 16500+i*310, 3.7, 11500+i*220, 2.6),
        ('纺织服饰','881162',dt, -1.6-i*0.06, -49000-i*900, -11.0, -29500-i*540, -6.6, -19500-i*360, -4.4, 16000+i*300, 3.6, 11200+i*215, 2.5),
        ('美容护理','881152',dt, -1.4-i*0.06, -46000-i*850, -10.2, -27500-i*510, -6.2, -18500-i*340, -4.0, 15000+i*280, 3.4, 10500+i*200, 2.4),
        ('贵金属','881132',dt, -1.3-i*0.05, -44000-i*800, -9.8, -26500-i*480, -6.0, -17500-i*320, -3.8, 14500+i*270, 3.2, 10000+i*190, 2.2);
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL generate_test_data();
DROP PROCEDURE IF EXISTS generate_test_data;
SQLDATA

    log_info "执行测试数据SQL..."
    ${MYSQL_CMD} "${MYSQL_DB}" < /tmp/ths_test_data.sql

    local total=$(${MYSQL_CMD} -N -e "SELECT COUNT(*) FROM industry_capital_flow" "${MYSQL_DB}" 2>/dev/null)
    log_info "测试数据灌入完成，共${total}条"

    state_done "test_data"
    log_step "========== 阶段4完成：数据就绪 =========="
}

# ==============================================================================
# 阶段5：API冒烟测试
# ==============================================================================
verify_api() {
    log_step "========== 阶段5：API冒烟测试 =========="

    local BASE="http://localhost:${APP_PORT}"

    log_info "GET /api/industry-flow/latest?topN=5"
    curl -s "${BASE}/api/industry-flow/latest?topN=5" | python3 -m json.tool 2>/dev/null | head -20 || log_warn "latest接口异常"

    log_info "GET /api/industry-flow/industries"
    curl -s "${BASE}/api/industry-flow/industries" | python3 -m json.tool 2>/dev/null | head -10 || log_warn "industries接口异常"

    log_info "GET /api/industry-flow/trend?industry=半导体&period=22"
    curl -s "${BASE}/api/industry-flow/trend?industry=%E5%8D%8A%E5%AF%BC%E4%BD%93&period=22" | python3 -m json.tool 2>/dev/null | head -15 || log_warn "trend接口异常"

    log_info "GET /api/industry-flow/history?industry=半导体&days=10"
    curl -s "${BASE}/api/industry-flow/history?industry=%E5%8D%8A%E5%AF%BC%E4%BD%93&days=10" | python3 -m json.tool 2>/dev/null | head -15 || log_warn "history接口异常"

    log_info "GET /api/industry-flow/resonance?shortPeriod=5&longPeriod=22"
    curl -s "${BASE}/api/industry-flow/resonance?shortPeriod=5&longPeriod=22" | python3 -m json.tool 2>/dev/null | head -15 || log_warn "resonance接口异常"

    log_step "========== 阶段5完成：API验证完毕 =========="
}

# ==============================================================================
# 停止应用
# ==============================================================================
stop_app() {
    if [ -f "${APP_PID_FILE}" ]; then
        local pid=$(cat "${APP_PID_FILE}")
        if kill -0 "${pid}" 2>/dev/null; then
            kill "${pid}" 2>/dev/null || true
            log_info "应用已停止(PID=${pid})"
        fi
        rm -f "${APP_PID_FILE}"
    else
        if lsof -ti:${APP_PORT} 2>/dev/null | grep -q .; then
            lsof -ti${APP_PORT} | xargs kill 2>/dev/null
            log_info "端口${APP_PORT}进程已终止"
        else
            log_info "无运行中的应用"
        fi
    fi
    state_reset "app_started"
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
    --build)
        build_project
        ;;
    --start)
        start_app
        ;;
    --data)
        insert_test_data
        ;;
    --verify)
        verify_api
        ;;
    --stop)
        stop_app
        ;;
    --status)
        check_status
        ;;
    --reset)
        reset_state
        ;;
    --all|"")
        setup_environment
        build_project
        start_app
        insert_test_data
        verify_api
        ;;
    *)
        echo "用法: bash mac-bootstrap.sh [选项]"
        echo ""
        echo "选项:"
        echo "  (无参数)    完整流程（环境+编译+启动+数据+验证）"
        echo "  --env       仅检查/安装环境依赖（JDK/Maven/MySQL/Redis）"
        echo "  --build     仅Maven编译打包"
        echo "  --start     仅启动Spring Boot"
        echo "  --data      仅灌入测试数据"
        echo "  --verify    仅API冒烟测试"
        echo "  --stop      停止应用"
        echo "  --status    查看当前环境状态"
        echo "  --reset     清除状态标记"
        echo ""
        echo "配置项（脚本顶部修改）:"
        echo "  PROJECT_DIR=${PROJECT_DIR}"
        echo "  MYSQL_USER=${MYSQL_USER}"
        echo "  MYSQL_PASS=${MYSQL_PASS:-'(空)'}"
        echo "  MYSQL_DB=${MYSQL_DB}"
        echo "  APP_PORT=${APP_PORT}"
        exit 1
        ;;
esac
