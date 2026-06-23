---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/cloud-computer-setup-guide.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782170648312
    ReservedCode2: ""
---
# 扣子平台云电脑环境安装踩坑复盘

> 首次使用时间：2026-06-23
> 云电脑配置：2vCPU / 4GiB / 40GB，Ubuntu 22.04，设备名"爬虫云服务器"

---

## 一、核心认知：bash 就在云电脑上执行

**这是最大的坑，也是最重要的发现。**

扣子平台的 `bash` 工具，当 `desktop_name` 设为云电脑设备名时，**默认就在云电脑终端执行**。不需要通过 `computer_use` 工具绕一圈。

### 错误路径（踩坑过程）

```
bash → 写脚本到本地 → computer_use → cat heredoc到云电脑终端 → 执行脚本
```

- 每条命令都要经过 computer_use，单条执行耗时 10-30 秒
- 容易触发 max_step 限制（默认 300 步）
- 容易触发 timeout（默认 120 秒）
- heredoc 方式传脚本，特殊字符容易出错

### 正确路径

```
bash(desktop_name="爬虫云服务器") → 直接执行命令
```

- 一条命令秒回
- 可以连续执行多条
- 输出直接返回，不需要截图

### 什么时候用 computer_use？

| 场景 | 用什么 |
|------|--------|
| apt install / 启动服务 / 编译代码 | **bash** |
| 查看进程 / 磁盘 / 网络 | **bash** |
| git / mvn / npm 操作 | **bash** |
| 需要浏览器交互（打开网页、点击按钮） | **computer_use** |
| 需要看 GUI 界面状态 | **computer_use** |

**原则：能用 bash 就用 bash，只有浏览器/GUI 操作才用 computer_use。**

---

## 二、云电脑安全限制

### 2.1 sudo 受限

云电脑当前用户就是 root，**大部分操作不需要 sudo**。

sudo 被限制为仅允许以下命令：
- `apt`
- `apt-get`
- `dpkg`

以下命令**被禁止**：
- `systemctl start/stop/restart`
- `service xxx start/stop`
- `sudo mysql`（非 apt 类 sudo 调用）

### 2.2 服务启动方式

因为 systemctl/service 被禁，必须用**直接执行二进制**方式启动服务：

| 服务 | 启动命令 | 备注 |
|------|----------|------|
| MySQL | `mysqld --user=mysql --daemonize` | 需先确保 `/var/run/mysqld` 目录存在且属主为 mysql |
| Redis | `redis-server --daemonize yes` | 直接可用 |

**MySQL 启动前置条件：**
```bash
mkdir -p /var/run/mysqld
chown mysql:mysql /var/run/mysqld
mysqld --user=mysql --daemonize
```

**验证服务是否启动：**
```bash
ps aux | grep mysqld | grep -v grep
redis-cli ping  # 应返回 PONG
```

---

## 三、MySQL 密码配置坑

### 3.1 默认认证方式

Ubuntu 22.04 的 MySQL 8.0 默认使用 `auth_socket` 认证插件。这意味着：

- `mysql -u root` 可以直接登录（走 socket 认证）
- `mysql -uroot -proot` 会报 `ERROR 1698 (28000): Access denied`

### 3.2 正确的改密流程

```bash
# 第一步：用 socket 认证登录（不加密码参数）
mysql -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;"

# 第二步：验证密码登录
mysql -uroot -proot -e "SELECT VERSION();"
```

### 3.3 错误示范

```bash
# ❌ 加了 -proot 但 socket 认证还没改
mysql -uroot -proot -e "ALTER USER ..."  # 报 Access denied

# ❌ 用 sudo mysql（sudo 被 intercept）
sudo mysql -e "ALTER USER ..."  # 报 sudo 拒绝

# ❌ 在 mysqld 启动前就尝试连接
mysql -u root -e "ALTER USER ..."  # 报 Can't connect
```

---

## 四、环境安装顺序推荐

### 4.1 一步到位的安装命令

```bash
apt-get install -y openjdk-17-jdk maven mysql-server redis-server
```

不需要分开装，一条命令搞定。安装完成后逐个验证：

```bash
java -version     # openjdk 17.x
mvn -version      # Apache Maven 3.x
mysql --version   # mysql 8.x
redis-cli --ping  # PONG（需先启动）
```

### 4.2 预装组件（无需安装）

| 组件 | 版本 | 说明 |
|------|------|------|
| Chrome | 146.x | 已预装，Playwright 可直接用 |
| Node.js | 22.x | 已预装，无需 nvm |
| Python | 3.10 | 系统自带 |
| git | 2.x | 系统自带 |

### 4.3 完整环境配置脚本

```bash
#!/bin/bash
# 扣子云电脑环境一键配置脚本
# 用法：bash setup-cloud-env.sh
set -e

echo "===== 安装依赖 ====="
apt-get update
apt-get install -y openjdk-17-jdk maven mysql-server redis-server

echo "===== 配置 MySQL ====="
mkdir -p /var/run/mysqld
chown mysql:mysql /var/run/mysqld
mysqld --user=mysql --daemonize
sleep 5
mysql -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'root'; FLUSH PRIVILEGES;"
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS ths_crawler DEFAULT CHARACTER SET utf8mb4;"

echo "===== 启动 Redis ====="
redis-server --daemonize yes

echo "===== 环境验证 ====="
echo "JDK:    $(java -version 2>&1 | head -1)"
echo "Maven:  $(mvn -version 2>&1 | head -1)"
echo "MySQL:  $(mysql -uroot -proot -e 'SELECT VERSION();' 2>&1 | tail -1)"
echo "Redis:  $(redis-cli ping)"
echo "Chrome: $(google-chrome --version)"
echo "Node:   $(node --version)"
```

---

## 五、踩坑时间线回顾

| 时间 | 操作 | 结果 | 耗时 | 原因 |
|------|------|------|------|------|
| 06:53 | computer_use 检查环境 | 发现 JDK11 需升级 | ~5min | 正常 |
| 06:58 | computer_use 装 JDK17+Maven | 成功 | ~2min | 正常 |
| 07:00 | computer_use 装 MySQL+Redis | apt 成功，启动失败 | ~3min | systemctl 被禁 |
| 07:03 | 写 cloud-setup.sh 上传 | 脚本创建成功 | ~2min | 方向对但执行方式错 |
| 07:03 | computer_use cat heredoc 执行 | 中断，sudo 问题 | ~5min | 不该用 computer_use |
| 07:04 | computer_use apt-get install | 成功 | ~1min | 去掉 sudo |
| 07:05 | computer_use 启动 MySQL+改密 | 失败，Access denied | ~2min | 没理解 socket 认证 |
| 07:06 | computer_use 检查进程 | 超时 | ~1min | computer_use 太慢 |
| 07:07 | computer_use cat heredoc 脚本 | max_step 超限 | ~1min | 还在绕路 |
| **07:08** | **bash 直接执行** | **全部成功** | **~30秒** | **正确方式** |

**总踩坑时长：约 15 分钟。如果一开始就用 bash，1 分钟搞定。**

---

## 六、总结：三条铁律

1. **bash 就是云电脑终端**，别用 computer_use 绕路
2. **云电脑已是 root**，不需要 sudo；sudo 只能跑 apt
3. **MySQL 改密码先走 socket 认证**，`mysql -u root`（不加密码）→ ALTER USER → 验证密码登录

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
