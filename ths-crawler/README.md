# ths-crawler 同花顺数据爬虫

> Java 17 + Spring Boot 3 + AKShare + Redis + MySQL

## 项目结构

```
ths-crawler/
├── pom.xml
├── scripts/
│   ├── fetch_sector_flow.py       # AKShare Python脚本
│   └── requirements.txt           # Python依赖
├── src/main/java/com/ths/crawler/
│   ├── ThsCrawlerApplication.java # 启动类
│   ├── core/                      # 核心接口层
│   │   ├── DataFetcher.java       # ⭐ 抓取器接口（扩展点）
│   │   ├── DataProcessor.java     # 处理器接口
│   │   ├── FetchContext.java      # 抓取上下文
│   │   └── FetchResult.java       # 抓取结果
│   ├── model/
│   │   ├── dto/                   # 数据传输对象
│   │   └── entity/                # MySQL实体
│   ├── mapper/                    # MyBatis-Plus Mapper
│   ├── fetcher/
│   │   ├── akshare/               # AKShare实现（当前）
│   │   │   └── AkshareSectorFlowFetcher.java
│   │   └── okhttp/                # OkHttp实现（占位，后续）
│   │       └── OkHttpSectorFlowFetcher.java
│   ├── processor/                 # 数据清洗
│   ├── storage/                   # 双写存储
│   ├── scheduler/                 # 定时任务
│   ├── controller/                # REST API
│   └── config/                    # 配置类
├── src/main/resources/
│   ├── application.yml
│   └── db/schema.sql              # 建表SQL
```

## 快速启动

### 1. 环境准备

```bash
# Python环境（AKShare依赖）
pip3 install -r scripts/requirements.txt

# MySQL建库建表
mysql -u root -p < src/main/resources/db/schema.sql

# 确认Redis运行
redis-cli ping
```

### 2. 配置修改

编辑 `application.yml`：
- MySQL密码
- Redis地址
- Python路径（如用虚拟环境需改python-path）

### 3. 启动

```bash
mvn spring-boot:run
```

### 4. 测试接口

```bash
# 手动触发抓取（行业前三）
curl http://localhost:8100/api/sector-flow/fetch?type=industry&topN=3

# 手动触发抓取（概念前三）
curl http://localhost:8100/api/sector-flow/fetch?type=concept&topN=3

# 触发完整日度抓取并存储
curl -X POST http://localhost:8100/api/sector-flow/daily

# 查询缓存的行业前三
curl http://localhost:8100/api/sector-flow/industry-top3

# 查询缓存的概念前三
curl http://localhost:8100/api/sector-flow/concept-top3
```

## 数据源切换

```yaml
# application.yml 中切换实现
ths:
  fetcher:
    sector-flow: akshare   # 当前方案
    # sector-flow: okhttp  # 后续原生HTTP方案
```

切换后重启即可，无需改代码。

## 扩展新的数据源

1. 实现 `DataFetcher<T>` 接口
2. 加 `@Component` + `@ConditionalOnProperty(name = "ths.fetcher.xxx", havingValue = "yyy")`
3. 在 `application.yml` 中配置切换项
4. 新增对应的 `DataProcessor` 和 `Entity`

## 定时任务

- 板块资金流向：每个交易日 15:30 自动执行
- 非交易日自动跳过（当前仅判断周末，节假日需接入交易日历）
