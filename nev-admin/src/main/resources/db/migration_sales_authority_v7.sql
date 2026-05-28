-- ============================================================
-- migration_sales_authority_v7.sql
-- v7 销量数据源权威化：销量板块只取官号（车企官博 + 行业协会），杜绝二手新闻失真。
--
-- 设计思路：
--   1. 给 official_account_config 加 is_authoritative_sales 字段（未来扩展用）
--   2. seed 2 个行业权威账号（乘联会、中汽协）—— UID 字段先占位，需用户手工补真实 UID
--   3. 把现有 6 个车企官号也标为权威（车企自己宣布的交付数据视为一手）
--   4. 行业 brand_keyword_config 加 2 个关键词，让 Bocha 也搜得到这俩源的转载
-- ============================================================

-- 1) 给 official_account_config 加权威销量数据源标记
ALTER TABLE official_account_config
  ADD COLUMN is_authoritative_sales TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '是否为权威销量数据源（乘联会/中汽协等行业协会标 1，车企官号默认也算权威）'
  AFTER is_enabled;

-- 2) seed 2 个行业权威账号（UID 占位，用户后台改正）
INSERT IGNORE INTO official_account_config
  (brand_name, platform, account_id, account_name, account_url,
   is_enabled, is_authoritative_sales, remark, add_ts, last_modify_ts) VALUES
('乘联会', 'wb', 'TODO_cpca_wb', '乘联分会',
  'https://weibo.com/cpcaauto', 1, 1,
  '行业权威销量数据源；UID 待用户从微博 PC 端 @乘联分会 主页 URL 复制真实 UID',
  UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('中汽协', 'wb', 'TODO_caam_wb', '中国汽车工业协会',
  'https://weibo.com/caaminfo', 1, 1,
  '行业权威销量数据源；UID 待用户从微博 PC 端 @中国汽车工业协会 主页 URL 复制真实 UID',
  UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);

-- 3) 现有 6 车企官号统一标为权威销量源（车企发的交付/销量数字是一手）
UPDATE official_account_config
  SET is_authoritative_sales = 1
  WHERE brand_name IN ('猛士', '仰望', '坦克', '方程豹', '问界', '路虎')
    AND platform = 'wb';

-- 4) 行业关键词补充（让 Bocha 也能搜到乘联会/中汽协的报道转载）
INSERT IGNORE INTO brand_keyword_config
  (brand_name, keyword, category, enabled, remark, add_ts, last_modify_ts) VALUES
('行业', '乘联会',  'industry', 1, '权威销量基准源', UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('行业', '中汽协',  'industry', 1, '权威销量基准源', UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
