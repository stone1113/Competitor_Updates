-- ============================================================
-- migration_event_v3.sql
-- 1) web_search_news 加 5 个事件分类字段
-- 2) news_url_blacklist 表 + 11 条种子（基于真实数据噪音模式）
-- ============================================================

-- 1. event_type 等 5 字段
ALTER TABLE web_search_news
  ADD COLUMN event_type VARCHAR(32) DEFAULT NULL
    COMMENT 'launch | price_finance | campaign | sales_milestone | other | spam | NULL(未分类)' AFTER category;
ALTER TABLE web_search_news
  ADD COLUMN event_importance TINYINT DEFAULT NULL
    COMMENT '0-10 LLM 评估的重要度' AFTER event_type;
ALTER TABLE web_search_news
  ADD COLUMN event_summary VARCHAR(500) DEFAULT NULL
    COMMENT 'LLM 提取的一句话事件摘要' AFTER event_importance;
ALTER TABLE web_search_news
  ADD COLUMN model_mentioned VARCHAR(255) DEFAULT NULL
    COMMENT 'LLM 提取的具体车型，逗号分隔' AFTER event_summary;
ALTER TABLE web_search_news
  ADD COLUMN extract_ts BIGINT DEFAULT NULL
    COMMENT '事件分类完成时间（NULL=未分类）' AFTER model_mentioned;

ALTER TABLE web_search_news
  ADD INDEX idx_event_type_addts (event_type, add_ts);
ALTER TABLE web_search_news
  ADD INDEX idx_extract_pending (extract_ts);

-- 2. URL 黑名单
CREATE TABLE IF NOT EXISTS news_url_blacklist (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pattern VARCHAR(255) NOT NULL COMMENT '匹配模式（不带 SQL LIKE 通配，Java 端 String.contains 判定）',
  reason VARCHAR(255),
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  add_ts BIGINT,
  last_modify_ts BIGINT,
  UNIQUE KEY uk_pattern (pattern),
  KEY idx_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新闻 URL 黑名单 — 采集时过滤';

-- 3. seed 黑名单（基于 v2 数据探查，命中 60% 噪音）
INSERT IGNORE INTO news_url_blacklist (pattern, reason, is_enabled, add_ts, last_modify_ts) VALUES
('autohome.com.cn/dealer/',   '汽车之家经销商页',       1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('autohome.com.cn/4s/',       '汽车之家 4S 店页',       1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('autohome.com.cn/promo/',    '汽车之家促销页',         1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('autohome.com.cn/spec/',     '汽车之家车款规格页',     1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('autohome.com.cn/config/',   '汽车之家配置页',         1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('bitauto.com/dealer/',       '易车经销商页',           1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('bitauto.com/4s/',           '易车 4S 店页',           1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('pcauto.com.cn/dealer/',     '太平洋经销商页',         1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('xcar.com.cn/4s/',           'XCar 4S 店页',           1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('/koubei/',                  '通用口碑列表页',         1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('/photolist/',               '通用图库列表',           1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
