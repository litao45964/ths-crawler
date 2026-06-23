-- ============================================================
-- schema_v2.sql - V2 新增表结构
-- 新增：行业资金日度明细表 + 行业资金趋势预统计表
-- 注意：不修改旧表，仅新增
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
