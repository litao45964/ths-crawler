-- ============================================================
-- Flyway V4: 创建A股交易日历表
-- 关联P0任务: P0-04 交易日历判定
-- 用途: 判定交易日/节假日/调休，前端日历控件灰置非交易日
-- 数据来源: 公开A股交易日历（需初始化灌入）
-- ============================================================

CREATE TABLE trade_calendar (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trade_date   DATE         NOT NULL COMMENT '交易日期',
    is_open      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否交易日: 1=是 0=否',
    year         INT          NOT NULL COMMENT '年份（冗余，加速按年查询）',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_date (trade_date),
    KEY idx_year (year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A股交易日历表';
