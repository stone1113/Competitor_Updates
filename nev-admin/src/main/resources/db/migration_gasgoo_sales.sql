-- ============================================================
-- migration_gasgoo_sales.sql
-- 盖世汽车销量数据：给每个对标车系映射盖世 series_id，按月抓销量。
--
-- 数据源：https://m.gasgoo.com/qcxl/cxxl/{year}/{month}/{series_id}
--   纯静态 HTML，含 4 行表格：车型/厂商/级别/SUV，列 = 当月/上月/上上月/本年累计
-- ============================================================

-- 1) autohome_series_config 加盖世 series_id 字段（手工填，无法自动映射）
ALTER TABLE autohome_series_config
  ADD COLUMN gasgoo_series_id VARCHAR(16) DEFAULT NULL
  COMMENT '盖世汽车 series_id（URL /qcxl/cxxl/Y/M/{id} 末尾段）；NULL=未配置不抓'
  AFTER series_id,
  ADD INDEX idx_gasgoo (gasgoo_series_id);

-- 2) 新增结构化销量记录表
CREATE TABLE IF NOT EXISTS gasgoo_sales_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  brand_name VARCHAR(64) NOT NULL COMMENT '对标品牌',
  model_name VARCHAR(128) NOT NULL COMMENT '车型名（与 autohome_series_config.model_name 对齐）',
  gasgoo_series_id VARCHAR(16) NOT NULL,
  period_year INT NOT NULL,
  period_month INT NOT NULL,
  sales_count INT NOT NULL COMMENT '该车型本月销量（辆）',
  brand_total INT DEFAULT NULL COMMENT '该车型所属厂商本月总销量',
  segment_total INT DEFAULT NULL COMMENT '同级别（如 D 级 / 中型 SUV）本月总销量',
  ytd_count INT DEFAULT NULL COMMENT '本年累计销量',
  source_url VARCHAR(512) NOT NULL,
  crawl_ts BIGINT NOT NULL,
  add_ts BIGINT,
  last_modify_ts BIGINT,
  UNIQUE KEY uk_series_period (gasgoo_series_id, period_year, period_month),
  KEY idx_brand_period (brand_name, period_year, period_month),
  KEY idx_period (period_year, period_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盖世汽车车型月销量（权威结构化）';

-- 3) seed 仰望 U8 = 2277（用户已确认；其余车型 URL 待用户从盖世手工查 series_id 后补）
UPDATE autohome_series_config SET gasgoo_series_id = '2277'
  WHERE brand_name = '仰望' AND model_name = '仰望U8';
