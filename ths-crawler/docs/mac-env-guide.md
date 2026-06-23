---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/mac-env-guide.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782170655322
    ReservedCode2: ""
---
# Mac 本地环境排查指南

> 场景：Mac 电脑，本地已安装 Redis 和 MySQL，如何验证现有环境是否满足 ths-crawler 要求，避免重复安装依赖，以及如何调整 setup.sh 脚本。

---

## 总体思路

```
先查已有什么 → 对比项目需要什么 → 差什么补什么 → 跳过已有的步骤
```

不要上来就跑 `setup.sh`，先逐项确认，再决定哪些步骤要跑、哪些跳过。

---

## Step 1：Python 环境

### 1.1 检查 Python3 是否已安装

```bash
# 查看 python3 路径和版本
which python3
python3 --version
```

**判断标准**：Python 3.8+ 即可。

### 1.2 检查 pip3

```bash
which pip3
pip3 --version
```

### 1.3 检查 akshare 是否已安装

```bash
python3 -c "import akshare; print(akshare.__version__)"
```

- **输出版本号** → 已安装，继续 Step 1.4
- **ModuleNotFoundError** → 未安装，执行：

```bash
pip3 install akshare pandas
```

### 1.4 检查 akshare 版本是否过旧

```bash
python3 -c "import akshare; print(akshare.__version__)"
# 输出如 1.14.x / 1.15.x 均可
```

若版本低于 1.14.0，升级：

```bash
pip3 install akshare --upgrade
```

### 1.5 快速验证 akshare 能否出数据

```bash
python3 scripts/fetch_sector_flow.py --type industry | head -c 200
```

- **输出 JSON 字符串**（含 board_type、data 数组）→ 正常
- **报错或输出空** → 可能是非交易日或网络问题，不一定是环境问题，换交易日再试

### ⚠️ macOS 常见坑：python3 指向 Homebrew 版本

```bash
# 如果 which python3 输出 /usr/bin/python3
# 这是系统自带版本，可能缺 pip
# 建议用 Homebrew 安装的版本：
brew list python@3
# 路径通常在 /opt/homebrew/bin/python3 (Apple Silicon) 或 /usr/local/bin/python3 (Intel)
```

如果 Homebrew Python3 存在但 `python3` 指向系统版本：

```bash
# 方案A：修改 PATH 让 Homebrew 版本优先（推荐）
echo 'export PATH="/opt/homebrew/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 方案B：在 application.yml 中指定 python 路径
# ths.akshare.python-path: /opt/homebrew/bin/python3
```

---

## Step 2：MySQL

### 2.1 检查 MySQL 是否运行

```bash
# 方式1：看进程
ps aux | grep mysql | grep -v grep

# 方式2：看端口
lsof -i :3306

# 方式3：直接连
mysql -u root -p -e "SELECT VERSION();"
```

**常见情况**：

| 输出 | 含义 | 下一步 |
|------|------|--------|
| 正常输出版本号 | MySQL 运行中 | 继续 2.2 |
| `Can't connect` | MySQL 未启动 | `brew services start mysql` |
| `command not found` | 未安装 | `brew install mysql` |

### 2.2 检查连接参数

项目默认配置（`application.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ths_crawler?useSSL=false&characterEncoding=utf8mb4&allowPublicKeyRetrieval=true
    username: root
    password: ${MYSQL_PASSWORD:root}
```

**确认你的 MySQL 实际配置**：

```bash
# 1. 端口是不是 3306？
mysql -u root -p -e "SHOW VARIABLES LIKE 'port';"

# 2. 用户名密码是什么？
# 如果你能用以下命令连上，说明默认配置可用：
mysql -u root -proot -e "SELECT 1;"

# 3. 如果你的 root 密码不是 root，别改 MySQL 密码，改项目配置：
```

**调整方式**：通过环境变量覆盖，不改代码文件：

```bash
export MYSQL_PASSWORD=你的真实密码
```

或在 `application.yml` 中直接改 `password` 字段。

### 2.3 检查是否已有 ths_crawler 数据库

```bash
mysql -u root -p -e "SHOW DATABASES LIKE 'ths_crawler';"
```

- **输出为空** → 需要建库，执行 schema.sql
- **已存在** → 检查表是否完整：

```bash
mysql -u root -p -e "USE ths_crawler; SHOW TABLES;"
```

期望看到 `ths_sector_capital_flow` 和 `ths_crawl_log` 两张表。如果表不完整，重新执行 schema.sql：

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

### ⚠️ macOS Homebrew MySQL 常见坑

```bash
# 如果用 Homebrew 装的 MySQL，socket 文件位置可能不同
# 报错 "Can't connect to local MySQL server through socket"
# 解决：明确指定 host
mysql -h 127.0.0.1 -u root -p

# 如果刚装完 MySQL 没设密码
mysql -u root  # 无密码直连
# 然后设密码
ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';
```

---

## Step 3：Redis

### 3.1 检查 Redis 是否运行

```bash
# 看进程
ps aux | grep redis-server | grep -v grep

# 看端口
lsof -i :6379

# 直接 ping
redis-cli ping
```

| 输出 | 含义 | 下一步 |
|------|------|--------|
| `PONG` | Redis 正常运行 | 继续 3.2 |
| `Could not connect` | Redis 未启动 | `brew services start redis` |
| `command not found` | 未安装 | `brew install redis` |

### 3.2 检查是否设了密码

```bash
redis-cli ping
# 如果返回 PONG → 无密码（项目默认配置即可）

# 如果返回 (error) NOAUTH
redis-cli -a 你的密码 ping
```

如果有密码，调整项目配置：

```bash
export REDIS_PASSWORD=你的密码
```

### 3.3 检查端口是否为默认 6379

```bash
redis-cli INFO server | grep tcp_port
```

如果不是 6379：

```bash
export REDIS_PORT=你的端口
```

---

## Step 4：Java / Maven

### 4.1 检查 Java 版本

```bash
java -version
```

**要求**：Java 17+（pom.xml 中 `<java.version>17</java.version>`）

| 当前版本 | 处理方式 |
|---------|---------|
| 17 / 21 | 直接用 |
| 8 / 11 | 需要升级，建议用 SDKMAN 管理多版本 |
| 未安装 | `brew install openjdk@17` |

### 4.2 检查 Maven

```bash
mvn -version
```

如果没有，用项目自带的 Maven Wrapper：

```bash
./mvnw -version  # 项目根目录下
```

---

## Step 5：根据排查结果调整 setup.sh

### 5.1 哪些步骤可以跳过

根据上面排查的结果，对照 setup.sh 的6个步骤：

| setup.sh 步骤 | 跳过条件 | 你可能的情况 |
|--------------|---------|------------|
| Step 1: 检查基础命令 | 全部已安装 | 大概率跳过 |
| Step 2: 安装Python依赖 | akshare已安装且版本OK | 看情况 |
| Step 3: 验证AKShare接口 | 不想在初始化时触发网络请求 | 可跳过 |
| Step 4: 初始化MySQL | 库和表已存在 | 看情况 |
| Step 5: 检查Redis | `redis-cli ping` 返回 PONG | 跳过 |
| Step 6: 编译Java项目 | 已编译过 | 首次需执行 |

### 5.2 推荐的最小执行路径

对于 Mac 本地已有 Redis + MySQL 的开发者，**不需要跑完整 setup.sh**，按这个顺序手动执行即可：

```bash
# 1. 确认 Python + akshare
python3 -c "import akshare; print(akshare.__version__)" || pip3 install akshare pandas

# 2. 建库建表（仅首次）
mysql -u root -p < src/main/resources/db/schema.sql

# 3. 确认 Redis
redis-cli ping

# 4. 编译
mvn clean compile -DskipTests

# 5. 设置环境变量（按你的实际情况）
export MYSQL_PASSWORD=你的密码    # 如果密码不是 root
export REDIS_PASSWORD=你的密码    # 如果 Redis 有密码

# 6. 启动
mvn spring-boot:run
```

### 5.3 如果仍想用 setup.sh，怎么改

不需要改脚本文件，通过环境变量控制即可：

```bash
# 只跑特定步骤（手动按步骤执行 setup.sh 中的函数）
# 或者直接传环境变量让脚本用你已有的服务

MYSQL_HOST=127.0.0.1 \
MYSQL_PASSWORD=你的密码 \
REDIS_PASSWORD=你的密码 \
bash scripts/setup.sh
```

### 5.4 可能需要改 application.yml 的地方

```yaml
# 如果 MySQL 密码不是 root
spring:
  datasource:
    password: ${MYSQL_PASSWORD:你的实际密码}  # 改默认值

# 如果 Redis 有密码
  data:
    redis:
      password: ${REDIS_PASSWORD:你的实际密码}

# 如果 python3 路径不在 PATH 中
ths:
  akshare:
    python-path: /opt/homebrew/bin/python3  # 改为实际路径
```

---

## Step 6：一键排查脚本

把上面的检查逻辑整合成一个轻量检查脚本，**只读不写不装**，纯诊断：

```bash
#!/bin/bash
# env-check.sh — 只检查不安装，5分钟摸清环境现状

echo "=========================================="
echo "  ths-crawler 环境诊断"
echo "=========================================="

# Python
echo -n "Python3: "
python3 --version 2>/dev/null || echo "❌ 未安装"
echo -n "pip3: "
pip3 --version 2>/dev/null | head -1 || echo "❌ 未安装"
echo -n "akshare: "
python3 -c "import akshare; print('✅ v' + akshare.__version__)" 2>/dev/null || echo "❌ 未安装"

# MySQL
echo -n "MySQL进程: "
ps aux | grep -v grep | grep -q mysql && echo "✅ 运行中" || echo "❌ 未运行"
echo -n "MySQL连接: "
mysql -h 127.0.0.1 -u root -proot -e "SELECT 1" &>/dev/null && echo "✅ root/root可连" || echo "❌ 连不上（检查密码）"
echo -n "ths_crawler库: "
mysql -h 127.0.0.1 -u root -proot -e "USE ths_crawler; SHOW TABLES;" &>/dev/null && echo "✅ 已建库建表" || echo "❌ 需初始化"

# Redis
echo -n "Redis进程: "
ps aux | grep -v grep | grep -q redis && echo "✅ 运行中" || echo "❌ 未运行"
echo -n "Redis连接: "
redis-cli ping 2>/dev/null | grep -q PONG && echo "✅ PONG" || echo "❌ 连不上"

# Java
echo -n "Java: "
java -version 2>&1 | head -1
echo -n "Maven: "
mvn -version 2>/dev/null | head -1 || echo "❌ 未安装（可用 ./mvnw 替代）"

echo "=========================================="
echo "  诊断完成，根据上述结果决定后续步骤"
echo "=========================================="
```

保存为 `scripts/env-check.sh`，执行：

```bash
chmod +x scripts/env-check.sh
bash scripts/env-check.sh
```

输出全部 ✅ 就可以直接 `mvn spring-boot:run` 启动了。

---

## 排查决策树

```
启动项目
  │
  ├─ 启动报错 "Cannot connect to MySQL"
  │   ├─ MySQL没启动 → brew services start mysql
  │   ├─ 密码不对 → export MYSQL_PASSWORD=xxx 或改 yml
  │   └─ 端口不是3306 → export MYSQL_PORT=xxx 或改 yml
  │
  ├─ 启动报错 "Unable to connect to Redis"
  │   ├─ Redis没启动 → brew services start redis
  │   ├─ 有密码没配 → export REDIS_PASSWORD=xxx 或改 yml
  │   └─ 端口不是6379 → export REDIS_PORT=xxx 或改 yml
  │
  ├─ 启动报错 "Python script execution failed"
  │   ├─ python3不在PATH → 改 ths.akshare.python-path
  │   ├─ akshare没装 → pip3 install akshare pandas
  │   └─ akshare版本过旧 → pip3 install akshare --upgrade
  │
  ├─ 启动成功但接口返回空数据
  │   ├─ 非交易日 → 正常，换交易日再测
  │   ├─ akshare接口变更 → pip3 install akshare --upgrade
  │   └─ 网络问题 → 检查代理设置
  │
  └─ 启动成功，接口返回正常数据 ✅
```

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
