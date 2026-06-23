-- ============================================================
-- V2__add_industry_link_fields.sql
-- 场景：V3新增行业详情页链接和领涨股代码字段
-- 触发：用户上传新表结构SQL脚本，要求同步3个新字段
-- 字段：industry_link/leading_stock_code/leading_stock_link
-- ============================================================

ALTER TABLE industry_capital_flow
    ADD COLUMN industry_link VARCHAR(300) DEFAULT NULL COMMENT '行业详情页URL' AFTER industry_name,
    ADD COLUMN leading_stock_code VARCHAR(10) DEFAULT NULL COMMENT '领涨股代码' AFTER leading_stock,
    ADD COLUMN leading_stock_link VARCHAR(300) DEFAULT NULL COMMENT '领涨股详情页URL' AFTER leading_stock_code;
