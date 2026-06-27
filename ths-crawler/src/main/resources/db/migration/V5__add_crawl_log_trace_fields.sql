-- ============================================================
-- V5__add_crawl_log_trace_fields.sql
-- 场景：P0-11 采集链路埋点，增强爬取日志可观测性
-- 触发：traceId贯穿 + 分阶段计时 + 重试计数
-- ============================================================

-- 新增 trace_id 链路追踪ID
ALTER TABLE crawl_log
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(32) DEFAULT NULL COMMENT '链路追踪ID' AFTER id;

-- 新增 phase 采集阶段
ALTER TABLE crawl_log
    ADD COLUMN IF NOT EXISTS phase VARCHAR(30) DEFAULT NULL COMMENT '采集阶段(fetch/save/total)' AFTER status;

-- 新增 detail 阶段详情（JSON）
ALTER TABLE crawl_log
    ADD COLUMN IF NOT EXISTS detail TEXT DEFAULT NULL COMMENT '阶段详情(JSON)' AFTER rows_saved;

-- 新增 retry_count 重试次数
ALTER TABLE crawl_log
    ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0 COMMENT '重试次数' AFTER detail;

-- 索引优化
CREATE INDEX IF NOT EXISTS idx_trace_id ON crawl_log(trace_id);
CREATE INDEX IF NOT EXISTS idx_phase ON crawl_log(phase);
