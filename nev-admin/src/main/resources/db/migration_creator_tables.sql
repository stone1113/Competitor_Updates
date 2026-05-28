-- ============================================================
-- 创作者/UP主辅助表（MediaCrawler 在主爬流程中会写这些表）
-- 缺这些表会导致主流程报错跳出（B站只爬到 1 条就是这个原因）
-- ============================================================

CREATE TABLE IF NOT EXISTS bilibili_up_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT,
    nickname        TEXT,
    sex             TEXT,
    sign            TEXT,
    avatar          TEXT,
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    total_fans      INT,
    total_liked     INT,
    user_rank       INT,
    is_official     INT,
    INDEX idx_bili_upinfo_uid (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B站UP主信息';

CREATE TABLE IF NOT EXISTS bilibili_contact_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    up_id           BIGINT,
    fan_id          BIGINT,
    up_name         TEXT,
    fan_name        TEXT,
    up_sign         TEXT,
    fan_sign        TEXT,
    up_avatar       TEXT,
    fan_avatar      TEXT,
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    INDEX idx_bili_contact_up (up_id),
    INDEX idx_bili_contact_fan (fan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B站UP主-粉丝关系';

CREATE TABLE IF NOT EXISTS bilibili_up_dynamic (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    dynamic_id      BIGINT,
    user_id         VARCHAR(255),
    user_name       TEXT,
    text            TEXT,
    type            TEXT,
    pub_ts          BIGINT,
    total_comments  INT,
    total_forwards  INT,
    total_liked     INT,
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    INDEX idx_bili_dyn_id (dynamic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B站UP主动态';

CREATE TABLE IF NOT EXISTS dy_creator (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(255),
    nickname        TEXT,
    avatar          TEXT,
    ip_location     TEXT,
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    `desc`          TEXT,
    gender          TEXT,
    follows         TEXT,
    fans            TEXT,
    interaction     TEXT,
    videos_count    VARCHAR(255),
    INDEX idx_dy_creator_uid (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抖音创作者';

CREATE TABLE IF NOT EXISTS xhs_creator (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(255),
    nickname        TEXT,
    avatar          TEXT,
    ip_location     TEXT,
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    `desc`          TEXT,
    gender          TEXT,
    follows         TEXT,
    fans            TEXT,
    interaction     TEXT,
    tag_list        TEXT,
    INDEX idx_xhs_creator_uid (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小红书创作者';

CREATE TABLE IF NOT EXISTS weibo_creator (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(255),
    nickname        TEXT,
    avatar          TEXT,
    ip_location     TEXT,
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    `desc`          TEXT,
    gender          TEXT,
    follows         TEXT,
    fans            TEXT,
    tag_list        TEXT,
    INDEX idx_wb_creator_uid (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博创作者';
