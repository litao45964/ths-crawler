#!/bin/bash
# ============================================================================
# ths-crawler 环境初始化脚本
# 用途：一键安装Python依赖、创建MySQL数据库和表、检查Redis连通性
# 使用：chmod +x scripts/setup.sh && ./scripts/setup.sh
# ============================================================================
#
# 【诞生背景 —— 当初为何要写这个脚本】
#
# 用户原话：
#   "1 把启动前命令，归纳写入脚本文件并加上注释"
#
# 场景还原：
#   项目代码已全部生成完毕，但启动前需要手动执行一系列操作：
#   - 检查 Python3/pip3 是否存在
#   - 安装 akshare + pandas 依赖
#   - 验证 AKShare 能正常拉数据
#   - 建库建表（MySQL）
#   - 确认 Redis 连通
#   - Maven 编译
#   每次手动敲太繁琐，所以归纳成一个脚本，一键跑完，每步加注释说明做了什么。
# ============================================================================

set -e

# ---------- 颜色输出 ----------
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ---------- 配置区（按实际环境修改） ----------
# MySQL
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DB="ths_crawler"

# Redis
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

# Python
PYTHON_CMD="${PYTHON_CMD:-python3}"
PIP_CMD="${PIP_CMD:-pip3}"

# 项目根目录（脚本所在目录的上级）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------- Step 1: 检查基础命令 ----------
info "===== Step 1: 检查基础命令 ====="

check_cmd() {
    if command -v "$1" &> /dev/null; then
        info "$1 已安装: $(command -v "$1")"
    else
        error "$1 未安装，请先安装后再运行此脚本"
        return 1
    fi
}

check_cmd "$PYTHON_CMD"
check_cmd "mysql"
check_cmd "redis-cli"
check_cmd "mvn" || check_cmd "mvnw" || warn "Maven未安装，请手动编译项目"

echo ""

# ---------- Step 2: 安装Python依赖 ----------
info "===== Step 2: 安装Python依赖 ====="

# 检查Python版本（需要3.8+）
PY_VERSION=$($PYTHON_CMD -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
info "Python版本: $PY_VERSION"

# 升级pip
info "升级pip..."
$PYTHON_CMD -m pip install --upgrade pip -q

# 安装requirements.txt中的依赖
REQUIREMENTS="$PROJECT_DIR/scripts/requirements.txt"
if [ -f "$REQUIREMENTS" ]; then
    info "安装Python依赖: $REQUIREMENTS"
    $PIP_CMD install -r "$REQUIREMENTS"
    info "Python依赖安装完成"
else
    warn "未找到 $REQUIREMENTS，跳过"
fi

# 验证akshare是否可用
info "验证akshare..."
$PYTHON_CMD -c "import akshare; print(f'  akshare版本: {akshare.__version__}')" || {
    error "akshare导入失败，请检查安装"
    exit 1
}

echo ""

# ---------- Step 3: 快速验证AKShare数据接口 ----------
info "===== Step 3: 验证AKShare数据接口 ====="

# 用Python脚本做一次行业板块数据抓取测试（仅取前3条）
info "测试抓取行业板块资金流向（仅验证，不存储）..."
TEST_RESULT=$($PYTHON_CMD "$PROJECT_DIR/scripts/fetch_sector_flow.py" --type industry 2>&1) || {
    warn "AKShare接口测试失败（可能是非交易日或网络问题）"
    warn "错误信息: $TEST_RESULT"
    warn "这不影响项目启动，可稍后手动验证"
}

if echo "$TEST_RESULT" | $PYTHON_CMD -c "import sys,json; d=json.load(sys.stdin); print(f'  行业板块数量: {len(d.get(\"data\",[]))}')" 2>/dev/null; then
    info "AKShare行业板块数据接口正常"
else
    warn "AKShare返回数据解析异常，可能是非交易日，不影响启动"
fi

echo ""

# ---------- Step 4: 创建MySQL数据库和表 ----------
info "===== Step 4: 初始化MySQL ====="

# 检查MySQL连通性
info "检查MySQL连通性: ${MYSQL_HOST}:${MYSQL_PORT}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" &>/dev/null || {
    error "MySQL连接失败，请检查配置: ${MYSQL_HOST}:${MYSQL_PORT} user=${MYSQL_USER}"
    error "可通过环境变量修改: MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD"
    exit 1
}
info "MySQL连接正常"

# 建库建表
SCHEMA_FILE="$PROJECT_DIR/src/main/resources/db/schema.sql"
if [ -f "$SCHEMA_FILE" ]; then
    info "执行建库建表脚本: $SCHEMA_FILE"
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" < "$SCHEMA_FILE"
    info "数据库初始化完成"
else
    warn "未找到 $SCHEMA_FILE，跳过"
fi

# 验证表是否创建成功
info "验证表结构..."
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e \
    "USE $MYSQL_DB; SHOW TABLES;" 2>/dev/null || warn "表验证失败，请手动检查"

echo ""

# ---------- Step 5: 检查Redis ----------
info "===== Step 5: 检查Redis ====="

REDIS_CLI_ARGS="-h $REDIS_HOST -p $REDIS_PORT"
if [ -n "$REDIS_PASSWORD" ]; then
    REDIS_CLI_ARGS="$REDIS_CLI_ARGS -a $REDIS_PASSWORD"
fi

info "检查Redis连通性: ${REDIS_HOST}:${REDIS_PORT}"
redis-cli $REDIS_CLI_ARGS PING 2>/dev/null | grep -q "PONG" || {
    error "Redis连接失败，请检查: ${REDIS_HOST}:${REDIS_PORT}"
    error "可通过环境变量修改: REDIS_HOST, REDIS_PORT, REDIS_PASSWORD"
    exit 1
}
info "Redis连接正常 (PONG)"

echo ""

# ---------- Step 6: 编译Java项目 ----------
info "===== Step 6: 编译Java项目 ====="

cd "$PROJECT_DIR"

if [ -f "mvnw" ]; then
    info "使用Maven Wrapper编译..."
    ./mvnw clean compile -DskipTests
elif command -v mvn &> /dev/null; then
    info "使用Maven编译..."
    mvn clean compile -DskipTests
else
    warn "未找到Maven，请手动编译: mvn clean compile -DskipTests"
fi

echo ""

# ---------- 完成 ----------
info "============================================"
info "  环境初始化完成！"
info "============================================"
echo ""
info "启动项目："
info "  cd $PROJECT_DIR"
info "  mvn spring-boot:run"
echo ""
info "验证接口："
info "  curl http://localhost:8100/api/sector-flow/fetch?type=industry&topN=3"
info "  curl http://localhost:8100/api/sector-flow/daily -X POST"
echo ""
info "配置文件："
info "  $PROJECT_DIR/src/main/resources/application.yml"
info "  关键配置项：ths.fetcher.sector-flow=akshare (当前) / okhttp (后续)"
echo ""
info "API测试文件："
info "  $PROJECT_DIR/docs/api-test-examples.http  (IntelliJ HTTP Client)"
