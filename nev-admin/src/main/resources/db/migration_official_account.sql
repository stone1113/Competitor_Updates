-- ============================================================
-- migration_official_account.sql
-- 官方账号抓取（v4 一手数据源）：MediaCrawler creator 模式 + ingest 到 web_search_news
-- ============================================================

CREATE TABLE IF NOT EXISTS official_account_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  brand_name VARCHAR(64) NOT NULL,
  platform VARCHAR(16) NOT NULL COMMENT 'wb | dy | xhs',
  account_id VARCHAR(64) NOT NULL COMMENT '平台 UID（微博）/ sec_uid（抖音）/ profile url id（小红书）',
  account_name VARCHAR(128) COMMENT '昵称',
  account_url VARCHAR(512) COMMENT '主页 URL（参考）',
  is_enabled TINYINT(1) NOT NULL DEFAULT 1,
  last_crawl_ts BIGINT,
  remark VARCHAR(255),
  add_ts BIGINT,
  last_modify_ts BIGINT,
  UNIQUE KEY uk_platform_account (platform, account_id),
  KEY idx_brand (brand_name),
  KEY idx_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='竞品官方账号配置（微博/抖音/小红书）';

-- seed 6 个微博官方账号（UID 用户后台可修正；抖音侧由用户从抖音 APP 复制 sec_uid 后补齐）
INSERT IGNORE INTO official_account_config
  (brand_name, platform, account_id, account_name, account_url, is_enabled, remark, add_ts, last_modify_ts)
VALUES
('理想',    'wb', '6196111796', '理想汽车',    'https://weibo.com/u/6196111796', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('问界',    'wb', '7676535647', 'AITO汽车',    'https://weibo.com/u/7676535647', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('特斯拉',  'wb', '1747567947', '特斯拉',      'https://weibo.com/u/1747567947', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('坦克',    'wb', '5995905081', '坦克汽车',    'https://weibo.com/u/5995905081', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('方程豹',  'wb', '7770859673', '方程豹汽车',  'https://weibo.com/u/7770859673', 1, 'UID 待验证', UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('仰望',    'wb', '7770860015', '仰望汽车',    'https://weibo.com/u/7770860015', 1, 'UID 待验证', UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
