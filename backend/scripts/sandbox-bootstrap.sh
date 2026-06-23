#!/bin/bash
# ==============================================================================
# ths-crawler 沙箱一键启动脚本（幂等版）
# ==============================================================================
# 诞生背景：
#   2026-06-18，ths-crawler项目在扣子云沙箱中反复验证。
#   沙箱重启后环境全丢（JDK/Maven/MySQL/Redis/源码），每次手动搭建耗时30-40分钟。
#   为了"不再重复造轮子"，特编写此脚本，沙箱重启后只需执行本脚本即可恢复完整环境。
#
# 核心设计原则：幂等性
#   每一步都先检测当前状态，已满足的就跳过，不重复下载/安装/配置。
#   目标：无论沙箱处于什么状态（全新/部分就绪/全部就绪），跑一遍都能到终态。
#
# 使用方式：
#   bash sandbox-bootstrap.sh           # 完整搭建（环境+代码+编译+启动+数据+验证）
#   bash sandbox-bootstrap.sh --env     # 仅搭建环境（JDK/Maven/MySQL/Redis）
#   bash sandbox-bootstrap.sh --code    # 仅下载代码+编译（环境已就绪时）
#   bash sandbox-bootstrap.sh --start   # 仅启动Spring Boot（已编译时）
#   bash sandbox-bootstrap.sh --data    # 仅灌入测试数据
#   bash sandbox-bootstrap.sh --verify  # 仅API冒烟测试
#   bash sandbox-bootstrap.sh --stop    # 停止应用
#   bash sandbox-bootstrap.sh --status  # 查看当前环境状态
#
# 前置条件：
#   1. 沙箱能访问外网（apt-get / coze CLI）
#   2. coze CLI已安装并认证（coze agent file命令可用）
#   3. 项目ID: 7652034661715165474
#
# 架构说明：
#   沙箱(Ubuntu 22.04) ← coze CLI download ← 项目空间(/ths-crawler/)
#   沙箱内编译运行，验证通过后产物上传回项目空间
# ==============================================================================

set -e

# ===== 全局配置 =====
PROJECT_ID="7652034661715165474"
# 沙箱编译目录（/tmp不会被workspace权限问题影响）
BUILD_DIR="/tmp/ths-crawler"
# Spring Boot端口
APP_PORT=8100
# MySQL配置（沙箱默认root无密码）
MYSQL_USER="root"
MYSQL_PASS="root"
MYSQL_DB="ths_crawler"
# PID文件
APP_PID_FILE="${BUILD_DIR}/app.pid"
# 状态标记文件目录（用于记录各步骤是否已完成，避免重复执行）
STATE_DIR="${BUILD_DIR}/.state"

# ===== 颜色输出 =====
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }
log_skip()  { echo -e "${CYAN}[SKIP]${NC} $1"; }

# ===== 幂等状态管理 =====
# 每个步骤完成后写一个标记文件到 STATE_DIR
# 下次执行时先检查标记，已完成的直接跳过
# 标记文件内容为完成时间戳，方便排查

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
    echo "====== ths-crawler 沙箱环境状态 ======"
    echo ""

    # JDK
    if java -version 2>&1 | grep -qE "17|21|23"; then
        echo -e "  JDK:       ${GREEN}✓ $(java -version 2>&1 | head -1)${NC}"
    else
        echo -e "  JDK:       ${RED}✗ 未安装或版本不符${NC}"
    fi

    # Maven
    if command -v mvn &>/dev/null; then
        echo -e "  Maven:     ${GREEN}✓ $(mvn -version 2>&1 | head -1)${NC}"
    else
        echo -e "  Maven:     ${RED}✗ 未安装${NC}"
    fi

    # MySQL
    if mysqladmin ping -u root -p"${MYSQL_PASS}" 2>/dev/null | grep -q "alive"; then
        local db_exists=$(mysql -u root -p"${MYSQL_PASS}" -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${MYSQL_DB}'" 2>/dev/null)
        local row_count=$(mysql -u root -p"${MYSQL_PASS}" -N -e "SELECT COUNT(*) FROM industry_capital_flow" "${MYSQL_DB}" 2>/dev/null || echo "0")
        echo -e "  MySQL:     ${GREEN}✓ 运行中，库=${MYSQL_DB}，数据=${row_count}条${NC}"
    elif command -v mysql &>/dev/null; then
        echo -e "  MySQL:     ${YELLOW}△ 已安装但未运行${NC}"
    else
        echo -e "  MySQL:     ${RED}✗ 未安装${NC}"
    fi

    # Redis
    if redis-cli ping 2>/dev/null | grep -q PONG; then
        echo -e "  Redis:     ${GREEN}✓ 运行中${NC}"
    elif command -v redis-cli &>/dev/null; then
        echo -e "  Redis:     ${YELLOW}△ 已安装但未运行${NC}"
    else
        echo -e "  Redis:     ${RED}✗ 未安装${NC}"
    fi

    # 源码
    if [ -d "${BUILD_DIR}/src" ]; then
        local java_count=$(find "${BUILD_DIR}/src" -name "*.java" 2>/dev/null | wc -l)
        echo -e "  源码:      ${GREEN}✓ ${java_count}个Java文件${NC}"
    else
        echo -e "  源码:      ${RED}✗ 未下载${NC}"
    fi

    # 编译产物
    if ls ${BUILD_DIR}/target/*.jar 2>/dev/null | head -1 | grep -q .; then
        local jar_path=$(ls ${BUILD_DIR}/target/*.jar 2>/dev/null | head -1)
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
# 阶段1：环境搭建（apt-get安装JDK/Maven/MySQL/Redis）
# 核心原则：每步先检测是否已满足，已满足则跳过
# ==============================================================================
setup_environment() {
    log_step "========== 阶段1：环境搭建（幂等）=========="

    # --- 1.1 清理残留的apt锁 ---
    # 沙箱崩溃重启后可能残留apt锁文件，导致apt-get失败
    # 这一步始终执行，因为检测成本极低，但锁文件会致命
    if fuser /var/lib/dpkg/lock-frontend 2>/dev/null; then
        log_info "检测到apt锁进程，等待释放..."
        sleep 5
    fi
    rm -f /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock /var/cache/apt/archives/lock 2>/dev/null || true
    dpkg --configure -a 2>/dev/null || true

    # --- 1.2 安装JDK 17 ---
    # 项目要求Java 17+（Spring Boot 3.x），沙箱Ubuntu 22.04默认源有openjdk-17
    # 检测逻辑：java命令存在且版本号包含17/21/23等17+版本号
    if java -version 2>&1 | grep -qE "version \"(1[7-9]|[2-9][0-9])"; then
        log_skip "JDK $(java -version 2>&1 | head -1) 已安装"
    else
        log_info "安装JDK 17..."
        apt-get update -qq 2>/dev/null || true
        apt-get install -y -qq openjdk-17-jdk-headless 2>&1 | tail -3
        log_info "JDK 安装完成: $(java -version 2>&1 | head -1)"
    fi

    # --- 1.3 安装Maven ---
    # 沙箱源自带Maven 3.6.x，够用
    if command -v mvn &>/dev/null; then
        log_skip "Maven $(mvn -version 2>&1 | head -1 | grep -oP '\d+\.\d+\.\d+') 已安装"
    else
        log_info "安装Maven..."
        apt-get update -qq 2>/dev/null || true
        apt-get install -y -qq maven 2>&1 | tail -3
        log_info "Maven 安装完成"
    fi

    # --- 1.4 安装MySQL ---
    # 沙箱用apt装的MySQL 8.0，root默认auth_socket认证（无需密码）
    # Spring Boot连接需要密码，后续步骤会改认证方式
    if dpkg -l mysql-server 2>/dev/null | grep -q "^ii"; then
        log_skip "MySQL 已安装"
    else
        log_info "安装MySQL..."
        apt-get update -qq 2>/dev/null || true
        apt-get install -y -qq mysql-server 2>&1 | tail -3
        log_info "MySQL 安装完成"
    fi

    # --- 1.5 启动MySQL ---
    # 检测逻辑：mysqladmin ping能返回alive说明MySQL已运行
    if mysqladmin ping -u root 2>/dev/null | grep -q "alive"; then
        log_skip "MySQL 已在运行"
    else
        log_info "启动MySQL..."
        service mysql start 2>&1 || true
        # 等待MySQL就绪（最多15秒）
        for i in $(seq 1 15); do
            if mysqladmin ping -u root 2>/dev/null | grep -q "alive"; then
                break
            fi
            sleep 1
        done
        if mysqladmin ping -u root 2>/dev/null | grep -q "alive"; then
            log_info "MySQL 启动成功"
        else
            log_error "MySQL 启动失败！"
            return 1
        fi
    fi

    # --- 1.6 配置MySQL root密码 ---
    # 沙箱MySQL 8.0默认root是auth_socket认证，
    # Spring Boot用密码连接，需切换为mysql_native_password
    # 检测逻辑：尝试用密码连接，能连上就跳过
    if mysql -u root -p"${MYSQL_PASS}" -e "SELECT 1" 2>/dev/null | grep -q "1"; then
        log_skip "MySQL root密码已配置"
    else
        log_info "配置MySQL root密码..."
        mysql -u root -e "
            ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${MYSQL_PASS}';
            FLUSH PRIVILEGES;
        " 2>/dev/null || true
        # 验证密码是否生效
        if mysql -u root -p"${MYSQL_PASS}" -e "SELECT 1" 2>/dev/null | grep -q "1"; then
            log_info "MySQL root密码配置成功"
        else
            log_warn "MySQL root密码配置可能未生效，但不影响继续"
        fi
    fi

    # --- 1.7 创建数据库和表 ---
    # 检测逻辑：数据库存在且4张核心表都在，则跳过
    local db_ok=false
    if mysql -u root -p"${MYSQL_PASS}" -N -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${MYSQL_DB}'" 2>/dev/null | grep -q "${MYSQL_DB}"; then
        local table_count=$(mysql -u root -p"${MYSQL_PASS}" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${MYSQL_DB}'" 2>/dev/null)
        if [ "${table_count}" -ge 4 ]; then
            db_ok=true
        fi
    fi

    if [ "${db_ok}" = true ]; then
        log_skip "数据库 ${MYSQL_DB} 及核心表已存在"
    else
        log_info "创建数据库 ${MYSQL_DB} 及表..."
        mysql -u root -p"${MYSQL_PASS}" -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} DEFAULT CHARSET utf8mb4;" 2>/dev/null

        mysql -u root -p"${MYSQL_PASS}" "${MYSQL_DB}" << 'EOSQL'
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
    fi

    # --- 1.8 安装并启动Redis ---
    # 检测逻辑：redis-cli ping返回PONG说明Redis已运行
    if redis-cli ping 2>/dev/null | grep -q PONG; then
        log_skip "Redis 已在运行"
    else
        if ! command -v redis-cli &>/dev/null; then
            log_info "安装Redis..."
            apt-get update -qq 2>/dev/null || true
            apt-get install -y -qq redis-server 2>&1 | tail -3
        else
            log_info "Redis已安装，启动中..."
        fi
        service redis-server start 2>&1 || true
        # ulimit警告可忽略，不影响功能
        if redis-cli ping 2>/dev/null | grep -q PONG; then
            log_info "Redis 启动成功"
        else
            log_warn "Redis 启动异常，但不影响核心功能"
        fi
    fi

    state_done "env_setup"
    log_step "========== 阶段1完成：环境就绪 =========="
}

# ==============================================================================
# 阶段2：从项目空间下载源码
# ==============================================================================
# 关键设计：
#   coze agent file download 不支持目录级联下载，只能逐文件下载。
#   这里把所有文件的"项目空间路径→本地路径"映射写死，避免每次手动探目录。
#   如果项目新增了Java文件，需要在这里补充映射。
#
# 幂等逻辑：
#   如果 BUILD_DIR/src 下已有31个Java文件且pom.xml存在，跳过整个下载。
#   如需强制重新下载，先删除 BUILD_DIR 或使用 --code --force
# ==============================================================================
download_code() {
    local force=false
    [ "${1}" = "--force" ] && force=true

    log_step "========== 阶段2：下载源码（幂等）=========="

    mkdir -p "${BUILD_DIR}"
    cd "${BUILD_DIR}"

    # --- 2.0 幂等检测：源码是否已完整 ---
    if [ "${force}" = false ] && [ -f "pom.xml" ]; then
        local java_count=$(find src -name "*.java" 2>/dev/null | wc -l)
        local expected_java=31
        if [ "${java_count}" -eq "${expected_java}" ]; then
            log_skip "源码已完整（${java_count}个Java文件），跳过下载。如需强制重新下载，使用 --code --force"
            return
        else
            log_warn "Java文件数不匹配（${java_count}/${expected_java}），重新下载..."
        fi
    fi

    # --- 2.1 创建目录结构 ---
    mkdir -p src/main/java/com/ths/crawler/{config,controller,core,fetcher/akshare,fetcher/okhttp,mapper,model/entity,model/dto,processor,scheduler,service,storage}
    mkdir -p src/main/resources/{mapper,db}

    # --- 2.2 文件映射表 ---
    # 格式："本地相对路径|项目空间路径"
    # ★★★ 新增Java文件后必须在这里补条目，否则编译时会缺文件！★★★
    #   这是铁律：编译是唯一真相，缺文件=编译失败=前功尽弃
    local FILES=(
        # ---- 构建文件 ----
        "pom.xml|/ths-crawler/pom.xml"
        # ---- 资源文件 ----
        "src/main/resources/application.yml|/ths-crawler/src/main/resources/application.yml"
        "src/main/resources/mapper/IndustryCapitalFlowMapper.xml|/ths-crawler/src/main/resources/mapper/IndustryCapitalFlowMapper.xml"
        "src/main/resources/mapper/IndustryTrendStatMapper.xml|/ths-crawler/src/main/resources/mapper/IndustryTrendStatMapper.xml"
        "src/main/resources/db/schema.sql|/ths-crawler/src/main/resources/db/schema.sql"
        "src/main/resources/db/schema_v2.sql|/ths-crawler/src/main/resources/db/schema_v2.sql"
        # ---- 启动类 ----
        "src/main/java/com/ths/crawler/ThsCrawlerApplication.java|/ths-crawler/src/main/java/com/ths/crawler/ThsCrawlerApplication.java"
        # ---- config ----
        "src/main/java/com/ths/crawler/config/AsyncConfig.java|/ths-crawler/src/main/java/com/ths/crawler/config/AsyncConfig.java"
        "src/main/java/com/ths/crawler/config/CorsConfig.java|/ths-crawler/src/main/java/com/ths/crawler/config/CorsConfig.java"
        "src/main/java/com/ths/crawler/config/MyBatisPlusConfig.java|/ths-crawler/src/main/java/com/ths/crawler/config/MyBatisPlusConfig.java"
        # ---- controller ----
        "src/main/java/com/ths/crawler/controller/IndustryFlowController.java|/ths-crawler/src/main/java/com/ths/crawler/controller/IndustryFlowController.java"
        "src/main/java/com/ths/crawler/controller/SectorFlowController.java|/ths-crawler/src/main/java/com/ths/crawler/controller/SectorFlowController.java"
        # ---- core（接口层）----
        "src/main/java/com/ths/crawler/core/DataFetcher.java|/ths-crawler/src/main/java/com/ths/crawler/core/DataFetcher.java"
        "src/main/java/com/ths/crawler/core/DataProcessor.java|/ths-crawler/src/main/java/com/ths/crawler/core/DataProcessor.java"
        "src/main/java/com/ths/crawler/core/FetchContext.java|/ths-crawler/src/main/java/com/ths/crawler/core/FetchContext.java"
        "src/main/java/com/ths/crawler/core/FetchResult.java|/ths-crawler/src/main/java/com/ths/crawler/core/FetchResult.java"
        # ---- fetcher/akshare（V1数据源）----
        "src/main/java/com/ths/crawler/fetcher/akshare/AkshareSectorFlowFetcher.java|/ths-crawler/src/main/java/com/ths/crawler/fetcher/akshare/AkshareSectorFlowFetcher.java"
        # ---- fetcher/okhttp（V2数据源，直连同花顺页面）----
        "src/main/java/com/ths/crawler/fetcher/okhttp/OkHttpSectorFlowFetcher.java|/ths-crawler/src/main/java/com/ths/crawler/fetcher/okhttp/OkHttpSectorFlowFetcher.java"
        "src/main/java/com/ths/crawler/fetcher/okhttp/ThsIndustryPageFetcher.java|/ths-crawler/src/main/java/com/ths/crawler/fetcher/okhttp/ThsIndustryPageFetcher.java"
        # ---- mapper ----
        "src/main/java/com/ths/crawler/mapper/CrawlLogMapper.java|/ths-crawler/src/main/java/com/ths/crawler/mapper/CrawlLogMapper.java"
        "src/main/java/com/ths/crawler/mapper/IndustryCapitalFlowMapper.java|/ths-crawler/src/main/java/com/ths/crawler/mapper/IndustryCapitalFlowMapper.java"
        "src/main/java/com/ths/crawler/mapper/IndustryTrendStatMapper.java|/ths-crawler/src/main/java/com/ths/crawler/mapper/IndustryTrendStatMapper.java"
        "src/main/java/com/ths/crawler/mapper/SectorCapitalFlowMapper.java|/ths-crawler/src/main/java/com/ths/crawler/mapper/SectorCapitalFlowMapper.java"
        # ---- model/entity（被大量import的底层类，上传/下载时优先确保）----
        "src/main/java/com/ths/crawler/model/entity/CrawlLogEntity.java|/ths-crawler/src/main/java/com/ths/crawler/model/entity/CrawlLogEntity.java"
        "src/main/java/com/ths/crawler/model/entity/IndustryCapitalFlowEntity.java|/ths-crawler/src/main/java/com/ths/crawler/model/entity/IndustryCapitalFlowEntity.java"
        "src/main/java/com/ths/crawler/model/entity/IndustryTrendStatEntity.java|/ths-crawler/src/main/java/com/ths/crawler/model/entity/IndustryTrendStatEntity.java"
        "src/main/java/com/ths/crawler/model/entity/SectorCapitalFlowEntity.java|/ths-crawler/src/main/java/com/ths/crawler/model/entity/SectorCapitalFlowEntity.java"
        # ---- model/dto ----
        "src/main/java/com/ths/crawler/model/dto/AkshareSectorFlowRawDTO.java|/ths-crawler/src/main/java/com/ths/crawler/model/dto/AkshareSectorFlowRawDTO.java"
        "src/main/java/com/ths/crawler/model/dto/IndustryFlowDTO.java|/ths-crawler/src/main/java/com/ths/crawler/model/dto/IndustryFlowDTO.java"
        "src/main/java/com/ths/crawler/model/dto/RegressionResult.java|/ths-crawler/src/main/java/com/ths/crawler/model/dto/RegressionResult.java"
        "src/main/java/com/ths/crawler/model/dto/SectorCapitalFlowDTO.java|/ths-crawler/src/main/java/com/ths/crawler/model/dto/SectorCapitalFlowDTO.java"
        "src/main/java/com/ths/crawler/model/dto/TrendResonanceDTO.java|/ths-crawler/src/main/java/com/ths/crawler/model/dto/TrendResonanceDTO.java"
        # ---- processor ----
        "src/main/java/com/ths/crawler/processor/SectorCapitalFlowProcessor.java|/ths-crawler/src/main/java/com/ths/crawler/processor/SectorCapitalFlowProcessor.java"
        # ---- scheduler ----
        "src/main/java/com/ths/crawler/scheduler/IndustryFlowJob.java|/ths-crawler/src/main/java/com/ths/crawler/scheduler/IndustryFlowJob.java"
        "src/main/java/com/ths/crawler/scheduler/SectorCapitalFlowJob.java|/ths-crawler/src/main/java/com/ths/crawler/scheduler/SectorCapitalFlowJob.java"
        # ---- service ----
        "src/main/java/com/ths/crawler/service/IndustryFlowService.java|/ths-crawler/src/main/java/com/ths/crawler/service/IndustryFlowService.java"
        "src/main/java/com/ths/crawler/service/TrendStatService.java|/ths-crawler/src/main/java/com/ths/crawler/service/TrendStatService.java"
        # ---- storage ----
        "src/main/java/com/ths/crawler/storage/DualWriteService.java|/ths-crawler/src/main/java/com/ths/crawler/storage/DualWriteService.java"
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

        # 幂等：如果本地文件已存在且非空且内容合法，跳过
        if [ -s "${local_path}" ] && ! head -1 "${local_path}" | grep -q '"ok"' 2>/dev/null; then
            skip=$((skip + 1))
            continue
        fi

        # cd到目标文件所在目录，download后文件以basename保存在当前目录
        local target_dir=$(dirname "${local_path}")
        local filename=$(basename "${local_path}")
        mkdir -p "${target_dir}"

        if (cd "${target_dir}" && coze agent file download \
            --project-id "${PROJECT_ID}" \
            --project-file-path "${remote_path}" 2>/dev/null); then
            # 验证：文件存在、非空、不是JSON状态信息（以{或[开头的是JSON不是源码）
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

    log_info "下载结果: ${ok} 新增, ${skip} 跳过(已存在), ${fail} 失败"

    # --- 2.4 下载后校验：比对Java源文件数 ---
    # 这是铁律第2条：上传/下载后做清单校验，防止漏文件
    local java_count=$(find src -name "*.java" | wc -l)
    local expected_java=31
    if [ "${java_count}" -ne "${expected_java}" ]; then
        log_warn "Java文件数不匹配！实际=${java_count}，期望=${expected_java}"
        log_warn "请检查文件映射表是否遗漏了新增文件"
    else
        log_info "Java文件数校验通过: ${java_count}个"
    fi

    state_done "code_download"
    log_step "========== 阶段2完成：源码就绪 =========="
}

# ==============================================================================
# 阶段3：Maven编译打包
# ==============================================================================
# 幂等逻辑：如果target目录下已有jar且比源码新，跳过编译。
# 如需强制重新编译，删除target目录后再执行。
# ==============================================================================
build_project() {
    log_step "========== 阶段3：编译打包（幂等）=========="

    cd "${BUILD_DIR}"

    # --- 3.0 幂等检测：是否已有有效的编译产物 ---
    if ls target/*.jar 2>/dev/null | head -1 | grep -q .; then
        local jar_path=$(ls target/*.jar 2>/dev/null | head -1)
        local jar_newer=true
        # 检查jar是否比所有Java源文件都新
        for src_file in $(find src -name "*.java" -newer "${jar_path}" 2>/dev/null); do
            jar_newer=false
            break
        done
        if [ "${jar_newer}" = true ]; then
            local jar_size=$(du -h "${jar_path}" | cut -f1)
            log_skip "编译产物已存在且比源码新: $(basename ${jar_path}) (${jar_size})。如需强制重编，删除target/后再执行"
            return
        else
            log_info "源码有更新，需要重新编译..."
        fi
    fi

    # --- 3.1 Maven编译 ---
    # -DskipTests：跳过测试（沙箱无测试环境）
    # -q：安静模式，只输出错误和警告
    # 注意：首次编译会下载依赖，耗时约2-3分钟
    log_info "开始Maven编译（首次会下载依赖，请耐心等待）..."
    mvn clean package -DskipTests -q 2>&1
    local mvn_exit=$?

    if [ ${mvn_exit} -eq 0 ] && ls target/*.jar 2>/dev/null | head -1 | grep -q .; then
        local jar_name=$(ls target/*.jar | head -1)
        local jar_size=$(du -h "${jar_name}" | cut -f1)
        log_info "编译成功！JAR: ${jar_name} (${jar_size})"
    else
        log_error "编译失败！尝试输出错误信息："
        mvn clean package -DskipTests 2>&1 | tail -30
        return 1
    fi

    state_done "build"
    log_step "========== 阶段3完成：编译就绪 =========="
}

# ==============================================================================
# 阶段4：启动Spring Boot
# ==============================================================================
# 幂等逻辑：如果应用已在运行（PID文件有效或端口被占用），跳过启动。
# ==============================================================================
start_app() {
    log_step "========== 阶段4：启动应用（幂等）=========="

    cd "${BUILD_DIR}"

    # --- 4.0 幂等检测：应用是否已在运行 ---
    if [ -f "${APP_PID_FILE}" ] && kill -0 "$(cat ${APP_PID_FILE})" 2>/dev/null; then
        log_skip "应用已在运行(PID=$(cat ${APP_PID_FILE}), 端口=${APP_PORT})"
        return
    fi

    # 也可以通过端口判断
    if curl -s "http://localhost:${APP_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "industryName"; then
        log_skip "检测到端口${APP_PORT}有应用在响应，跳过启动"
        return
    fi

    # --- 4.1 清理旧进程 ---
    if [ -f "${APP_PID_FILE}" ]; then
        local old_pid=$(cat "${APP_PID_FILE}")
        kill "${old_pid}" 2>/dev/null || true
        rm -f "${APP_PID_FILE}"
    fi

    # 检查端口占用
    if ss -tlnp 2>/dev/null | grep -q ":${APP_PORT}"; then
        log_warn "端口${APP_PORT}被占用，尝试释放..."
        fuser -k "${APP_PORT}/tcp" 2>/dev/null || true
        sleep 2
    fi

    # --- 4.2 启动Spring Boot ---
    local jar_path=$(ls target/*.jar | head -1)
    log_info "启动Spring Boot: ${jar_path}"

    java -jar "${jar_path}" \
        --spring.datasource.password="${MYSQL_PASS}" \
        > app.log 2>&1 &
    local pid=$!
    echo "${pid}" > "${APP_PID_FILE}"

    # --- 4.3 等待启动完成 ---
    log_info "等待应用启动（最多60秒）..."
    local started=false
    for i in $(seq 1 60); do
        if curl -s "http://localhost:${APP_PORT}/api/industry-flow/latest?topN=1" 2>/dev/null | grep -q "."; then
            started=true
            break
        fi
        if grep -q "Started ThsCrawlerApplication" app.log 2>/dev/null; then
            started=true
            break
        fi
        sleep 1
    done

    if [ "${started}" = true ]; then
        log_info "应用启动成功！PID=${pid}，端口=${APP_PORT}"
        log_info "API基础地址: http://localhost:${APP_PORT}"
    else
        log_error "应用启动超时！查看日志："
        tail -30 app.log
        return 1
    fi

    state_done "app_started"
    log_step "========== 阶段4完成：应用运行中 =========="
}

# ==============================================================================
# 阶段5：灌入测试数据（可选）
# ==============================================================================
# 说明：测试数据用于验证API功能，不依赖爬虫抓取。
# 生成10个交易日×40个行业的模拟数据，分4组趋势。
# 幂等逻辑：如果已有数据则跳过，不会重复灌入。
# ==============================================================================
insert_test_data() {
    log_step "========== 阶段5：灌入测试数据（幂等）=========="

    # 检查是否已有数据
    local existing=$(mysql -u root -p"${MYSQL_PASS}" -N -e \
        "SELECT COUNT(*) FROM industry_capital_flow" "${MYSQL_DB}" 2>/dev/null)
    if [ "${existing}" -gt 0 ]; then
        log_skip "已有${existing}条数据，跳过灌入。如需重新灌入，先清空表再执行"
        return
    fi

    log_info "生成测试数据SQL（40行业 × 10交易日 = 400条）..."

    cat > /tmp/test_data.sql << 'SQLDATA'
-- 测试数据：40行业 × 10交易日 = 400条
-- 分4组趋势：强势流入 / 温和流入 / 资金流出 / 强流出

DROP PROCEDURE IF EXISTS generate_test_data;
DELIMITER //
CREATE PROCEDURE generate_test_data()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE dt DATE;
    DECLARE dates_array VARCHAR(200) DEFAULT '2026-06-03,2026-06-04,2026-06-05,2026-06-06,2026-06-09,2026-06-10,2026-06-11,2026-06-12,2026-06-13,2026-06-16';
    
    -- 强势流入组（斜率8000~10000，10行业）
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

    # --- 执行SQL ---
    log_info "执行测试数据SQL..."
    mysql -u root -p"${MYSQL_PASS}" "${MYSQL_DB}" < /tmp/test_data.sql 2>/dev/null

    # 验证
    local total=$(mysql -u root -p"${MYSQL_PASS}" -N -e "SELECT COUNT(*) FROM industry_capital_flow" "${MYSQL_DB}" 2>/dev/null)
    log_info "测试数据灌入完成，共${total}条"

    state_done "test_data"
    log_step "========== 阶段5完成：数据就绪 =========="
}

# ==============================================================================
# 阶段6：API验证（冒烟测试）
# ==============================================================================
verify_api() {
    log_step "========== 阶段6：API冒烟测试 =========="

    local BASE="http://localhost:${APP_PORT}"

    # --- 6.1 latest ---
    log_info "GET /api/industry-flow/latest?topN=5"
    curl -s "${BASE}/api/industry-flow/latest?topN=5" | python3 -m json.tool 2>/dev/null | head -20 || log_warn "latest接口异常"

    # --- 6.2 industries ---
    log_info "GET /api/industry-flow/industries"
    curl -s "${BASE}/api/industry-flow/industries" | python3 -m json.tool 2>/dev/null | head -10 || log_warn "industries接口异常"

    # --- 6.3 trend ---
    log_info "GET /api/industry-flow/trend?industry=半导体&period=22"
    curl -s "${BASE}/api/industry-flow/trend?industry=%E5%8D%8A%E5%AF%BC%E4%BD%93&period=22" | python3 -m json.tool 2>/dev/null | head -15 || log_warn "trend接口异常"

    # --- 6.4 history ---
    log_info "GET /api/industry-flow/history?industry=半导体&days=10"
    curl -s "${BASE}/api/industry-flow/history?industry=%E5%8D%8A%E5%AF%BC%E4%BD%93&days=10" | python3 -m json.tool 2>/dev/null | head -15 || log_warn "history接口异常"

    # --- 6.5 resonance ---
    log_info "GET /api/industry-flow/resonance?shortPeriod=5&longPeriod=22"
    curl -s "${BASE}/api/industry-flow/resonance?shortPeriod=5&longPeriod=22" | python3 -m json.tool 2>/dev/null | head -15 || log_warn "resonance接口异常"

    log_step "========== 阶段6完成：API验证完毕 =========="
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
        fuser -k "${APP_PORT}/tcp" 2>/dev/null && log_info "端口${APP_PORT}进程已终止" || log_info "无运行中的应用"
    fi
    state_reset "app_started"
}

# ==============================================================================
# 重置所有状态（慎用！会清空状态标记，下次执行将重新走全流程）
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
        download_code
        build_project
        start_app
        insert_test_data
        verify_api
        ;;
    *)
        echo "用法: bash sandbox-bootstrap.sh [选项]"
        echo ""
        echo "选项:"
        echo "  (无参数)    完整搭建（环境+代码+编译+启动+数据+验证）"
        echo "  --env       仅搭建环境（JDK/Maven/MySQL/Redis）"
        echo "  --code      仅下载代码+编译（加--force强制重新下载）"
        echo "  --start     仅启动Spring Boot"
        echo "  --data      仅灌入测试数据"
        echo "  --verify    仅API冒烟测试"
        echo "  --stop      停止应用"
        echo "  --status    查看当前环境状态"
        echo "  --reset     清除状态标记（下次全量执行）"
        echo ""
        echo "幂等性说明："
        echo "  每一步都会检测当前状态，已满足则跳过。"
        echo "  沙箱重启后可直接执行，已有的环境不会重复安装。"
        exit 1
        ;;
esac
