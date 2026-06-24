#!/bin/bash
# ============================================================
# shellzdh - 沙箱一键部署脚本（shell自动化）
# 用法: ./shellzdh.sh "commit message"
# 流程: 编译(铁律②) → push GitHub(铁律④) → 上传项目空间 → 输出云电脑同步命令
# ============================================================

set -e

# ---------- 配置 ----------
REPO_DIR="/app/data/所有对话/主对话/ths-crawler-git"
JAVA_DIR="$REPO_DIR/ths-crawler"
PROJECT_ID="7652034661715165474"

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

# ---------- 参数 ----------
MSG="${1:?用法: ./shellzdh.sh \"commit message\"}"

# ---------- 前置检查 ----------
[ -d "$REPO_DIR/.git" ] || fail "不是git仓库: $REPO_DIR"
cd "$REPO_DIR"

# ============================================================
# 步骤1: Maven编译（铁律② 编译是唯一真相）
# ============================================================
step "1/4 Maven编译"
cd "$JAVA_DIR"
if mvn clean compile -q 2>&1; then
    info "编译通过"
else
    fail "编译失败，终止部署（铁律②：编译不过不推送）"
fi

# ============================================================
# 步骤2: Git push（铁律④ 先push再上传）
# ============================================================
step "2/4 Push GitHub"
cd "$REPO_DIR"
git add -A

if git diff --cached --quiet; then
    warn "无变更，跳过commit/push"
else
    git commit -m "$MSG"
    if git push origin main 2>&1; then
        info "已push: $MSG"
    else
        fail "push失败，检查网络或凭证"
    fi
fi

COMMIT=$(git rev-parse --short HEAD)

# ============================================================
# 步骤3: 上传项目空间（留档备份）
# ============================================================
step "3/4 上传项目空间"

# 上传后端jar产物（如果存在）
JAR_FILE="$JAVA_DIR/target/ths-crawler-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    if coze agent file upload --project-id "$PROJECT_ID" \
       --local-file-path "$JAR_FILE" \
       --project-dir "/ths-crawler/deploy" 2>&1 | grep -q '"ok"'; then
        info "jar已上传到项目空间"
    else
        warn "jar上传失败（非阻塞，GitHub已是权威）"
    fi
else
    warn "jar未生成（需mvn package），跳过上传"
fi

# 上传变更的文档文件到项目空间
CHANGED_DOCS=$(git diff --name-only HEAD~1 2>/dev/null | grep '^ths-crawler/docs/' || true)
for f in $CHANGED_DOCS; do
    LOCAL_PATH="$REPO_DIR/$f"
    PROJ_DIR="/$(dirname "$f")"
    PROJ_FILE="/$f"
    if [ -f "$LOCAL_PATH" ]; then
        if coze agent file upload --project-id "$PROJECT_ID" \
           --local-file-path "$LOCAL_PATH" \
           --project-dir "$PROJ_DIR" 2>&1 | grep -q '"ok"'; then
            info "文档已上传: $f"
        else
            warn "文档上传失败: $f"
        fi
    fi
done

# ============================================================
# 步骤4: 输出云电脑同步命令
# ============================================================
step "4/4 云电脑同步"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info "沙箱侧全部完成 (commit: $COMMIT)"
echo ""
warn "接下来在云电脑执行:"
echo ""
echo "  cd /root/ths-crawler-repo && git pull && bash shellzdh-cloud.sh"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
