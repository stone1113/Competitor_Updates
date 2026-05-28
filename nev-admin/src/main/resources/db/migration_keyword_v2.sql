-- ============================================================
-- migration_keyword_v2.sql
-- 关键词中心 v2：
--  1) brand_keyword_config 加 category 列（self/competitor/industry）
--  2) web_search_news 加 category 列（self/competitor/industry，
--     兼容旧 'brand'/'industry' 取值；新代码统一写 self/competitor/industry）
--  3) 清空旧 seed 12 条，重新 seed 35 条（4 self + 21 competitor + 10 industry）
-- ============================================================

-- 1. brand_keyword_config 加 category
ALTER TABLE brand_keyword_config
  ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'self'
  COMMENT 'self | competitor | industry' AFTER keyword;
ALTER TABLE brand_keyword_config
  ADD INDEX idx_category (category);

-- 2. web_search_news 加 category（默认 brand 兼容旧数据）
ALTER TABLE web_search_news
  ADD COLUMN category VARCHAR(16) NOT NULL DEFAULT 'brand'
  COMMENT 'self | competitor | industry | brand(legacy)' AFTER brand_name;
ALTER TABLE web_search_news
  ADD INDEX idx_category_addts (category, add_ts);

-- 3. 清旧 seed，重新 seed
DELETE FROM brand_keyword_config;

-- 本品 (4)
INSERT INTO brand_keyword_config (brand_name, keyword, category, enabled, remark, add_ts, last_modify_ts) VALUES
('猛士', '猛士汽车', 'self', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('猛士', '猛士M817', 'self', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('猛士', '猛士917',  'self', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('猛士', '猛士M9',   'self', 1, '预研车型', UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);

-- 竞品 (21)
INSERT INTO brand_keyword_config (brand_name, keyword, category, enabled, remark, add_ts, last_modify_ts) VALUES
('仰望',    '仰望U8',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('仰望',    '仰望U9',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('方程豹',  '方程豹豹5',     'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('方程豹',  '方程豹豹8',     'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('坦克',    '坦克300',      'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('坦克',    '坦克500',      'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('坦克',    '坦克700',      'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('问界',    '问界M7',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('问界',    '问界M8',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('问界',    '问界M9',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('理想',    '理想L7',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('理想',    '理想L8',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('理想',    '理想L9',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('理想',    '理想MEGA',     'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('智界',    '智界R7',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('智界',    '智界S7',       'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('阿维塔',  '阿维塔07',     'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('阿维塔',  '阿维塔12',     'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('特斯拉',  'Model Y',     'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('智己',    '智己LS7',      'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('领克',    '领克09 EM-P',  'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);

-- 行业 (10)
INSERT INTO brand_keyword_config (brand_name, keyword, category, enabled, remark, add_ts, last_modify_ts) VALUES
('行业', '新能源汽车',         'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '电动越野',           'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '纯电SUV',            'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '增程式电动',         'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '华为乾崑',           'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', 'L3 自动驾驶',         'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '800V 高压平台',       'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', 'CLTC 续航',          'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '新能源汽车销量',     'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '北京车展',           'industry', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
