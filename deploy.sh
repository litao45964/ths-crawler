#!/bin/bash
# ============================================================
# ths-crawler 一键部署脚本（云电脑 Ubuntu 22.04）
# 用法：git clone git@github.com:litao45964/ths-crawler.git
#       cd ths-crawler && chmod +x deploy.sh && ./deploy.sh
# ============================================================

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# 默认配置（可通过环境变量覆盖）
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DB="${MYSQL_DB:-ths_crawler}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
APP_PORT="${APP_PORT:-8100}"
NGINX_PORT="${NGINX_PORT:-8080}"

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

# ============================================================
# 1. 环境检测
# ============================================================
info "=== 第1步/6步：环境检测 ==="

check_cmd() {
    if command -v "$1" &>/dev/null; then
        info "$1 ✅ ($(command -v "$1"))"
        return 0
    else
        warn "$1 ❌ 未安装"
        return 1
    fi
}

MISSING=0
check_cmd java    || MISSING=1
check_cmd mvn     || MISSING=1
check_cmd mysql   || MISSING=1
check_cmd redis-server || MISSING=1
check_cmd node    || MISSING=1
check_cmd npm     || MISSING=1
check_cmd nginx   || MISSING=1

if [ "$MISSING" = "1" ]; then
    echo ""
    warn "缺少以上组件，正在尝试安装..."
    apt-get update -qq
    for pkg in openjdk-17-jdk maven mysql-server redis-server nodejs npm nginx; do
        dpkg -l "$pkg" &>/dev/null || {
            info "安装 $pkg..."
            apt-get install -y -qq "$pkg" 2>/dev/null || warn "$pkg 安装失败，请手动安装"
        }
    done
fi

# ============================================================
# 2. MySQL 建库 + 导入 schema
# ============================================================
info "=== 第2步/6步：MySQL 初始化 ==="

# 启动MySQL（云电脑systemctl被禁，直接启动进程）
if ! pgrep mysqld &>/dev/null; then
    info "启动 MySQL..."
    mysqld --user=mysql --datadir=/var/lib/mysql &
    sleep 5
fi

# 创建数据库（如果不存在）
mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS \`$MYSQL_DB\` DEFAULT CHARSET utf8mb4;" 2>/dev/null || \
mysql -u"$MYSQL_USER" -e "CREATE DATABASE IF NOT EXISTS \`$MYSQL_DB\` DEFAULT CHARSET utf8mb4;" 2>/dev/null || \
    warn "创建数据库失败，可能已存在或密码不对"

# 导入schema
if [ -f "$BASE_DIR/schema.sql" ]; then
    info "导入 schema.sql..."
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$BASE_DIR/schema.sql" 2>/dev/null || \
    mysql -u"$MYSQL_USER" "$MYSQL_DB" < "$BASE_DIR/schema.sql" 2>/dev/null || \
        warn "schema导入失败，请手动导入"
else
    warn "schema.sql 不存在，跳过"
fi

# 导入测试数据（如果有）
if [ -f "$BASE_DIR/ths-crawler/scripts/test_data.sql" ]; then
    info "导入测试数据..."
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" < "$BASE_DIR/ths-crawler/scripts/test_data.sql" 2>/dev/null || true
fi

# ============================================================
# 3. 启动 Redis
# ============================================================
info "=== 第3步/6步：启动 Redis ==="
if ! pgrep redis-server &>/dev/null; then
    redis-server --daemonize yes
    info "Redis 已启动"
else
    info "Redis 已在运行"
fi

# ============================================================
# 4. 后端编译启动
# ============================================================
info "=== 第4步/6步：后端编译启动 ==="

# 写入application.yml的环境变量配置
export MYSQL_PASSWORD
export REDIS_HOST
export REDIS_PORT

cd "$BASE_DIR/ths-crawler"
info "Maven 编译..."
mvn clean package -DskipTests -q 2>&1 || error "Maven编译失败"

info "启动 Spring Boot (端口 $APP_PORT)..."
nohup java -jar target/*.jar \
    --server.port="$APP_PORT" \
    --spring.datasource.password="$MYSQL_PASSWORD" \
    > /tmp/ths-crawler.log 2>&1 &

BACKEND_PID=$!
info "后端 PID: $BACKEND_PID，等待启动..."

# 等待后端就绪
for i in $(seq 1 30); do
    if curl -s "http://localhost:$APP_PORT/api/industry-flow/latest?topN=1" &>/dev/null; then
        info "后端启动成功 ✅"
        break
    fi
    if [ "$i" = "30" ]; then
        warn "后端30秒内未就绪，请检查日志: /tmp/ths-crawler.log"
    fi
    sleep 1
done

# ============================================================
# 5. 前端构建部署
# ============================================================
info "=== 第5步/6步：前端构建部署 ==="

cd "$BASE_DIR/ths-crawler-ui"
info "npm install..."
npm install --quiet 2>&1 | tail -1

info "vite build..."
npx vite build 2>&1 | tail -3

info "部署到 /var/www/ths-crawler..."
mkdir -p /var/www/ths-crawler
cp -r dist/* /var/www/ths-crawler/

# 配置nginx
if [ ! -f /etc/nginx/sites-available/ths-crawler ]; then
    info "配置 Nginx..."
    cp "$BASE_DIR/nginx.conf" /etc/nginx/sites-available/ths-crawler
    ln -sf /etc/nginx/sites-available/ths-crawler /etc/nginx/sites-enabled/
fi

nginx -t 2>&1 && nginx -s reload 2>/dev/null || nginx 2>/dev/null
info "Nginx 已启动 (端口 $NGINX_PORT)"

# ============================================================
# 6. Cloudflare Tunnel
# ============================================================
info "=== 第6步/6步：Cloudflare Tunnel ==="

if command -v cloudflared &>/dev/null; then
    # 检查是否有命名隧道配置
    if [ -f "$BASE_DIR/cloudflared.yml" ]; then
        info "启动命名隧道..."
        nohup cloudflared tunnel run --config "$BASE_DIR/cloudflared.yml" ths-crawler > /tmp/cf-tunnel.log 2>&1 &
    else
        info "启动临时隧道（URL每次重启会变）..."
        nohup cloudflared tunnel --url "http://localhost:$NGINX_PORT" > /tmp/cf-tunnel.log 2>&1 &
    fi
    sleep 3
    TUNNEL_URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' /tmp/cf-tunnel.log | head -1)
    if [ -n "$TUNNEL_URL" ]; then
        info "公网访问地址: $TUNNEL_URL"
    else
        info "隧道启动中，查看日志: tail -f /tmp/cf-tunnel.log"
    fi
else
    warn "cloudflared 未安装，跳过隧道配置"
    info "安装方式: curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /usr/local/bin/cloudflared && chmod +x /usr/local/bin/cloudflared"
fi

# ============================================================
# 完成
# ============================================================
echo ""
echo "============================================"
info "🚀 部署完成！"
echo "============================================"
echo "  本地访问:  http://localhost:$NGINX_PORT"
echo "  后端API:   http://localhost:$APP_PORT/api/industry-flow/latest?topN=5"
echo "  公网地址:  ${TUNNEL_URL:-未配置}"
echo ""
echo "  后端日志:  tail -f /tmp/ths-crawler.log"
echo "  隧道日志:  tail -f /tmp/cf-tunnel.log"
echo "============================================"
