-- ============================================================
-- V5__add_crawl_log_trace_fields.sql
-- 场景：P0-11 采集链路埋点，增强爬取日志可观测性
-- ============================================================
ALTER TABLE ths_crawl_log ADD COLUMN trace_id VARCHAR(32) DEFAULT NULL COMMENT '链路追踪ID' AFTER id;
ALTER TABLE ths_crawl_log ADD COLUMN phase VARCHAR(30) DEFAULT NULL COMMENT '采集阶段' AFTER status;
ALTER TABLE ths_crawl_log ADD COLUMN detail TEXT DEFAULT NULL COMMENT '阶段详情(JSON)' AFTER cost_ms;
ALTER TABLE ths_crawl_log ADD COLUMN retry_count INT DEFAULT 0 COMMENT '重试次数' AFTER detail;
