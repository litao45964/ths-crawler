-- 同花顺爬虫数据库初始化脚本

CREATE DATABASE IF NOT EXISTS ths_crawler DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE ths_crawler;

-- 板块资金流向每日快照
CREATE TABLE IF NOT EXISTS ths_sector_capital_flow (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    board_name      VARCHAR(30) NOT NULL COMMENT '板块名称',
    board_type      VARCHAR(10) NOT NULL COMMENT 'industry=行业, concept=概念',
    change_percent  DECIMAL(10,3) COMMENT '涨跌幅%',
    main_net_inflow DECIMAL(18,2) COMMENT '主力净流入(元)',
    main_inflow     DECIMAL(18,2) COMMENT '主力流入(元)',
    main_outflow    DECIMAL(18,2) COMMENT '主力流出(元)',
    lead_stock      VARCHAR(20) COMMENT '领涨股',
    lead_change     DECIMAL(10,3) COMMENT '领涨股涨幅%',
    flow_rank       INT COMMENT '当日排名',
    trade_date      DATE NOT NULL COMMENT '交易日期',
    raw_json        JSON COMMENT '原始JSON',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_date_rank (board_type, trade_date, flow_rank),
    KEY idx_trade_date (trade_date),
    KEY idx_board_type (board_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='板块资金流向';

-- 抓取任务日志
CREATE TABLE IF NOT EXISTS ths_crawl_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    source          VARCHAR(30) NOT NULL COMMENT '数据源标识',
    status          VARCHAR(10) NOT NULL COMMENT 'SUCCESS/FAIL/PARTIAL',
    record_count    INT COMMENT '抓取记录数',
    cost_ms         BIGINT COMMENT '耗时毫秒',
    error_msg       TEXT COMMENT '错误信息',
    trade_date      DATE NOT NULL COMMENT '交易日期',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_source_date (source, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抓取任务日志';
