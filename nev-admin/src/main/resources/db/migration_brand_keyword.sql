-- ============================================================
-- 品牌-关键词配置表（爬虫触发的预设关键词）
-- 一行一个 keyword，方便单独启停
-- ============================================================

CREATE TABLE IF NOT EXISTS brand_keyword_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name      VARCHAR(128) NOT NULL,
    keyword         VARCHAR(255) NOT NULL,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    remark          VARCHAR(255),
    add_ts          BIGINT NOT NULL,
    last_modify_ts  BIGINT NOT NULL,
    UNIQUE KEY uk_brand_keyword (brand_name, keyword),
    INDEX idx_brand (brand_name),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌爬取关键词配置';

-- Seed defaults (与 BrandConfigProperties.initDefaults 保持一致)
INSERT IGNORE INTO brand_keyword_config (brand_name, keyword, enabled, add_ts, last_modify_ts) VALUES
  ('猛士',   '猛士汽车',   1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('猛士',   '猛士M817',  1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('猛士',   '猛士917',   1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('猛士',   '猛士M9',    1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('坦克',   '坦克汽车',   1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('坦克',   '坦克500',   1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('坦克',   '坦克700',   1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('方程豹', '方程豹汽车', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('方程豹', '豹5',       1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('方程豹', '豹8',       1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('问界',   '问界M7',    1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('问界',   '问界M9',    1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
