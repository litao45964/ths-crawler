#!/bin/bash
# ============================================
# ths-crawler 云电脑环境一键安装脚本
# 适用：扣子云电脑（Ubuntu 22.04, root用户）
# 限制：sudo仅允许apt，禁止systemctl/service
# 创建时间：2026-06-23
# ============================================

set -e

echo "===== 1. 安装JDK 17 ====="
if java -version 2>&1 | grep -q "17"; then
    echo "JDK 17 已安装，跳过"
else
    apt-get install -y openjdk-17-jdk
    echo "JDK 17 安装完成"
fi
java -version 2>&1

echo ""
echo "===== 2. 安装Maven ====="
if which mvn > /dev/null 2>&1; then
    echo "Maven 已安装，跳过"
else
    apt-get install -y maven
    echo "Maven 安装完成"
fi
mvn -version 2>&1 | head -1

echo ""
echo "===== 3. 安装MySQL 8.0 ====="
if which mysql > /dev/null 2>&1; then
    echo "MySQL 已安装"
else
    apt-get install -y mysql-server
    echo "MySQL 安装完成"
fi

# 启动MySQL（不用systemctl/service）
if ! pgrep mysqld > /dev/null 2>&1; then
    echo "启动MySQL..."
    mysqld --user=root --daemonize 2>/dev/null || {
        # 如果daemonize失败，尝试其他方式
        echo "尝试用mysqld_safe启动..."
        nohup mysqld --user=root > /tmp/mysql.log 2>&1 &
        sleep 5
    }
fi

# 等MySQL就绪
for i in $(seq 1 10); do
    if mysql -e "SELECT 1;" > /dev/null 2>&1; then
        echo "MySQL 已启动"
        break
    fi
    echo "等待MySQL就绪... ($i/10)"
    sleep 2
done

# 改root密码（socket方式连接再改）
mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;" 2>/dev/null || {
    echo "ALTER USER失败，尝试CREATE USER..."
    mysql -e "CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost'; FLUSH PRIVILEGES;" 2>/dev/null || {
        echo "⚠️  MySQL密码修改失败，请手动执行："
        echo "   mysql -e \"ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;\""
    }
}

# 创建数据库
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS ths_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || \
mysql -e "CREATE DATABASE IF NOT EXISTS ths_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null && \
echo "数据库 ths_crawler 创建成功"

mysql -uroot -proot -e "SELECT VERSION();" 2>/dev/null || mysql -e "SELECT VERSION();"

echo ""
echo "===== 4. 安装Redis ====="
if which redis-server > /dev/null 2>&1; then
    echo "Redis 已安装"
else
    apt-get install -y redis-server
    echo "Redis 安装完成"
fi

if ! pgrep redis-server > /dev/null 2>&1; then
    echo "启动Redis..."
    redis-server --daemonize yes
fi
redis-cli ping

echo ""
echo "===== 5. 安装Node.js ====="
if which node > /dev/null 2>&1; then
    echo "Node.js 已安装: $(node --version)"
else
    curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
    apt-get install -y nodejs
    echo "Node.js 安装完成: $(node --version)"
fi

echo ""
echo "===== 6. 验证Chrome ====="
if which google-chrome > /dev/null 2>&1; then
    echo "Chrome 已安装: $(google-chrome --version)"
else
    echo "⚠️  Chrome 未安装"
fi

echo ""
echo "===== 7. 配置Maven阿里云镜像 ====="
mkdir -p ~/.m2
cat > ~/.m2/settings.xml << 'MAVEN_EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
MAVEN_EOF
echo "Maven阿里云镜像配置完成"

echo ""
echo "===== 安装完成 ====="
echo "环境信息："
echo "  JDK:    $(java -version 2>&1 | head -1)"
echo "  Maven:  $(mvn -version 2>&1 | head -1)"
echo "  MySQL:  $(mysql -uroot -proot -e "SELECT VERSION();" 2>/dev/null || echo '待验证')"
echo "  Redis:  $(redis-cli ping 2>/dev/null || echo '待验证')"
echo "  Node:   $(node --version 2>/dev/null || echo '待验证')"
echo "  Chrome: $(google-chrome --version 2>/dev/null || echo '待验证')"
echo "  磁盘:   $(df -h / | tail -1 | awk '{print $4 " 可用"}')"
