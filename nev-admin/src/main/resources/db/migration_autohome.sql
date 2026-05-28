-- 汽车之家车型参数同步：两张表
-- autohome_series_config: 要抓取的车系白名单（前端可编辑/扩展）
-- autohome_spec: 抓取结果，按 (spec_id, param_name, crawl_date) 唯一

CREATE TABLE IF NOT EXISTS autohome_series_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name VARCHAR(64) NOT NULL COMMENT '品牌名',
    model_name VARCHAR(128) NOT NULL COMMENT '车型名',
    series_id VARCHAR(32) NOT NULL COMMENT '汽车之家 seriesid',
    role VARCHAR(16) NOT NULL DEFAULT 'competitor' COMMENT 'self | competitor',
    is_enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) DEFAULT NULL,
    add_ts BIGINT,
    last_modify_ts BIGINT,
    UNIQUE KEY uk_series_id (series_id),
    KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='汽车之家车系抓取配置';

CREATE TABLE IF NOT EXISTS autohome_spec (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    series_id VARCHAR(32) NOT NULL COMMENT '汽车之家 seriesid',
    spec_id VARCHAR(32) NOT NULL COMMENT '车款 id (specid)',
    spec_name VARCHAR(255) DEFAULT NULL COMMENT '车款全名，如"猛士M817 2026款 Ultimate"',
    param_category VARCHAR(64) DEFAULT NULL COMMENT '参数类别：基本参数/动力/底盘/智驾...',
    param_name VARCHAR(128) NOT NULL COMMENT '参数名：最大功率/续航里程...',
    param_value VARCHAR(512) DEFAULT NULL COMMENT '参数值',
    crawl_date DATE NOT NULL COMMENT '抓取日期',
    add_ts BIGINT,
    UNIQUE KEY uk_spec_param_date (spec_id, param_name, crawl_date),
    KEY idx_series_date (series_id, crawl_date),
    KEY idx_crawl_date (crawl_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='汽车之家车型参数明细';

-- v1 seed: 我方 2 车 + 6 核心竞品（series_id 占位，运行时按需调整）
INSERT IGNORE INTO autohome_series_config (brand_name, model_name, series_id, role, is_enabled, add_ts, last_modify_ts) VALUES
('猛士',     '猛士M817',    '7172', 'self',       1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('猛士',     '猛士917',     '5856', 'self',       1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('比亚迪',   '仰望U8',      '7227', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('方程豹',   '方程豹豹5',   '7234', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('理想',     '理想L9',      '5990', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('问界',     '问界M9',      '6755', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('坦克',     '坦克500',     '5292', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('特斯拉',   'Model Y',     '4182', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
