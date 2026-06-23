#!/bin/bash
# ths-crawler 云电脑环境配置脚本（第二阶段：启动服务+配置）
# 在云电脑终端直接运行: bash /tmp/setup2.sh
set +e

echo "===== 检查MySQL进程 ====="
ps aux | grep mysqld | grep -v grep

echo "===== 尝试启动MySQL ====="
# 先确保目录存在
mkdir -p /var/run/mysqld
chown mysql:mysql /var/run/mysqld 2>/dev/null

# 用mysqld_safe启动（更稳定）
mysqld_safe --user=mysql &
sleep 5

# 再试另一种
if ! pgrep mysqld > /dev/null; then
    mysqld --user=mysql --daemonize 2>/dev/null
    sleep 3
fi

echo "===== 检查MySQL进程 ====="
ps aux | grep mysqld | grep -v grep

echo "===== 测试MySQL连接（socket方式） ====="
mysql -u root -e "SELECT 1;" 2>&1

echo "===== 改MySQL root密码 ====="
mysql -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;" 2>&1

echo "===== 测试密码登录 ====="
mysql -uroot -proot -e "SELECT VERSION();" 2>&1

echo "===== 创建数据库 ====="
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS ths_crawler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>&1

echo "===== 启动Redis ====="
redis-server --daemonize yes 2>/dev/null
sleep 1
redis-cli ping 2>&1

echo "===== Node.js ====="
which node 2>/dev/null && node --version || echo "Node not installed"

echo "===== 全部环境状态 ====="
echo "JDK:    $(java -version 2>&1 | head -1)"
echo "Maven:  $(mvn -version 2>&1 | head -1)"
echo "MySQL:  $(mysql -uroot -proot -e 'SELECT VERSION();' 2>&1)"
echo "Redis:  $(redis-cli ping 2>&1)"
echo "Chrome: $(google-chrome --version 2>&1)"
echo "Node:   $(node --version 2>/dev/null || echo 'NOT_INSTALLED')"
echo "Disk:   $(df -h / | tail -1 | awk '{print $4}')"
