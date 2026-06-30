-- ============================================================
-- V1__baseline_init_schema.sql
-- 场景：Flyway基线脚本，打包现有5张表的完整DDL
-- 触发：引入Flyway数据库版本化管控，需要一个基线起点
-- 说明：在已有数据库上，baseline-on-migrate=true会标记V1为"已执行"而不实际跑SQL
--       新环境（沙箱重启）会从V1开始全量建表
-- ============================================================

-- 表1: 行业资金日度明细表
CREATE TABLE IF NOT EXISTS industry_capital_flow (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trade_date DATE NOT NULL COMMENT '交易日期',
    industry_code VARCHAR(20) NOT NULL COMMENT '行业代码（881xxx）',
    industry_name VARCHAR(50) NOT NULL COMMENT '行业名称',
    net_amount DECIMAL(20, 4) NOT NULL COMMENT '净额（万元）',
    inflow_amount DECIMAL(20, 4) DEFAULT 0 COMMENT '流入额（万元）',
    outflow_amount DECIMAL(20, 4) DEFAULT 0 COMMENT '流出额（万元）',
    industry_change_pct DECIMAL(10, 4) DEFAULT NULL COMMENT '行业涨跌幅（%）',
    leading_stock VARCHAR(50) DEFAULT NULL COMMENT '领涨股',
    leading_stock_pct DECIMAL(10, 4) DEFAULT NULL COMMENT '领涨股涨幅（%）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_trade_date_industry (trade_date, industry_name),
    KEY idx_trade_date (trade_date),
    KEY idx_net_amount (net_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业资金流水日度明细表';

-- 表2: 行业资金趋势预统计表
CREATE TABLE IF NOT EXISTS industry_trend_stat (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trade_date DATE NOT NULL COMMENT '统计日期',
    industry_name VARCHAR(50) NOT NULL COMMENT '行业名称',
    stat_period INT NOT NULL COMMENT '统计周期（天数）',
    sample_count INT NOT NULL COMMENT '实际有效样本量',
    trend_slope DECIMAL(20, 10) DEFAULT NULL COMMENT '线性回归斜率',
    intercept DECIMAL(20, 10) DEFAULT NULL COMMENT '线性回归截距',
    r_squared DECIMAL(10, 6) DEFAULT NULL COMMENT '拟合优度R²',
    total_net_amount DECIMAL(20, 4) DEFAULT NULL COMMENT '周期内净额总和',
    avg_net_amount DECIMAL(20, 4) DEFAULT NULL COMMENT '周期内净额均值',
    std_net_amount DECIMAL(20, 4) DEFAULT NULL COMMENT '周期内净额标准差',
    min_net_amount DECIMAL(20, 4) DEFAULT NULL COMMENT '周期内最小净额',
    max_net_amount DECIMAL(20, 4) DEFAULT NULL COMMENT '周期内最大净额',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_period_industry (trade_date, stat_period, industry_name),
    KEY idx_trade_date (trade_date),
    KEY idx_stat_period (stat_period),
    KEY idx_slope (trend_slope),
    KEY idx_rsquared (r_squared)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行业资金趋势预统计表';

-- 表3: 板块资金流向表（概念板块）
CREATE TABLE IF NOT EXISTS sector_capital_flow (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    trade_date DATE NOT NULL COMMENT '交易日期',
    sector_name VARCHAR(100) NOT NULL COMMENT '板块名称',
    sector_type VARCHAR(20) NOT NULL DEFAULT 'concept' COMMENT '板块类型(concept/industry)',
    net_amount DECIMAL(20, 4) NOT NULL COMMENT '净额（万元）',
    inflow_amount DECIMAL(20, 4) DEFAULT 0 COMMENT '流入额（万元）',
    outflow_amount DECIMAL(20, 4) DEFAULT 0 COMMENT '流出额（万元）',
    change_pct DECIMAL(10, 4) DEFAULT NULL COMMENT '涨跌幅（%）',
    leading_stock VARCHAR(50) DEFAULT NULL COMMENT '领涨股',
    leading_stock_pct DECIMAL(10, 4) DEFAULT NULL COMMENT '领涨股涨幅（%）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_trade_date_sector (trade_date, sector_name, sector_type),
    KEY idx_trade_date (trade_date),
    KEY idx_net_amount (net_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块资金流向表';

-- 表4: 爬取日志表（与CrawlLogEntity对齐）
CREATE TABLE IF NOT EXISTS ths_crawl_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(50) NOT NULL COMMENT '数据源标识',
    status VARCHAR(20) NOT NULL COMMENT '状态(SUCCESS/FAIL/PARTIAL)',
    record_count INT DEFAULT 0 COMMENT '抓取记录数',
    cost_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    error_msg TEXT DEFAULT NULL COMMENT '错误信息',
    trade_date DATE DEFAULT NULL COMMENT '交易日期',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_source (source),
    KEY idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬取日志表';

-- 表5: 数据校验表
CREATE TABLE IF NOT EXISTS data_verify_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    verify_date DATE NOT NULL COMMENT '校验日期',
    table_name VARCHAR(50) NOT NULL COMMENT '表名',
    expected_rows INT DEFAULT 0 COMMENT '预期行数',
    actual_rows INT DEFAULT 0 COMMENT '实际行数',
    status VARCHAR(20) NOT NULL COMMENT '状态(pass/fail)',
    remark TEXT DEFAULT NULL COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_verify_date (verify_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据校验日志表';
