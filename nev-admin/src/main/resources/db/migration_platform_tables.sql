-- ============================================================
-- 平台表迁移：对齐 MediaCrawler 的 SQLAlchemy ORM 列定义
-- ============================================================
-- 背景：原 schema.sql 里的 5 张内容表 + 5 张评论表是按业务最小集设计的，
-- 与 MediaCrawler 实际写入的字段不一致（缺 avatar/ip_location/source_keyword 等）。
-- 本脚本 DROP 这 10 张表（已确认全空）后按 MediaCrawler ORM 重建，
-- 同时保留 brand_name 列（BettaFish 已 patch 写入）+ brand_name 索引（查询性能）。

DROP TABLE IF EXISTS xhs_note;
DROP TABLE IF EXISTS xhs_note_comment;
DROP TABLE IF EXISTS douyin_aweme;
DROP TABLE IF EXISTS douyin_aweme_comment;
DROP TABLE IF EXISTS bilibili_video;
DROP TABLE IF EXISTS bilibili_video_comment;
DROP TABLE IF EXISTS weibo_note;
DROP TABLE IF EXISTS weibo_note_comment;
DROP TABLE IF EXISTS kuaishou_video;
DROP TABLE IF EXISTS kuaishou_video_comment;

-- -----------------------------------------------------------
-- 小红书
-- -----------------------------------------------------------
CREATE TABLE xhs_note (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(255),
    nickname            TEXT,
    avatar              TEXT,
    ip_location         TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    note_id             VARCHAR(255),
    type                TEXT,
    title               TEXT,
    `desc`              TEXT,
    video_url           TEXT,
    `time`              BIGINT COMMENT '毫秒时间戳',
    last_update_time    BIGINT,
    liked_count         TEXT,
    collected_count     TEXT,
    comment_count       TEXT,
    share_count         TEXT,
    image_list          TEXT,
    tag_list            TEXT,
    note_url            TEXT,
    source_keyword      TEXT,
    brand_name          VARCHAR(128),
    xsec_token          TEXT,
    INDEX idx_xhs_note_id (note_id),
    INDEX idx_xhs_time (`time`),
    INDEX idx_xhs_brand_time (brand_name, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小红书笔记';

CREATE TABLE xhs_note_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(255),
    nickname            TEXT,
    avatar              TEXT,
    ip_location         TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    comment_id          VARCHAR(255),
    create_time         BIGINT,
    note_id             VARCHAR(255),
    content             TEXT,
    sub_comment_count   INT,
    pictures            TEXT,
    parent_comment_id   VARCHAR(255),
    like_count          TEXT,
    brand_name          VARCHAR(128),
    INDEX idx_xhs_cmt_id (comment_id),
    INDEX idx_xhs_cmt_time (create_time),
    INDEX idx_xhs_cmt_note (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小红书评论';

-- -----------------------------------------------------------
-- 抖音
-- -----------------------------------------------------------
CREATE TABLE douyin_aweme (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(255),
    sec_uid             VARCHAR(255),
    short_user_id       VARCHAR(255),
    user_unique_id      VARCHAR(255),
    nickname            TEXT,
    avatar              TEXT,
    user_signature      TEXT,
    ip_location         TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    aweme_id            BIGINT,
    aweme_type          TEXT,
    title               TEXT,
    `desc`              TEXT,
    create_time         BIGINT COMMENT '秒时间戳',
    liked_count         TEXT,
    comment_count       TEXT,
    share_count         TEXT,
    collected_count     TEXT,
    aweme_url           TEXT,
    cover_url           TEXT,
    video_download_url  TEXT,
    music_download_url  TEXT,
    note_download_url   TEXT,
    source_keyword      TEXT,
    brand_name          VARCHAR(128),
    INDEX idx_dy_aweme_id (aweme_id),
    INDEX idx_dy_create_time (create_time),
    INDEX idx_dy_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抖音视频';

CREATE TABLE douyin_aweme_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(255),
    sec_uid             VARCHAR(255),
    short_user_id       VARCHAR(255),
    user_unique_id      VARCHAR(255),
    nickname            TEXT,
    avatar              TEXT,
    user_signature      TEXT,
    ip_location         TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    comment_id          BIGINT,
    aweme_id            BIGINT,
    content             TEXT,
    create_time         BIGINT,
    sub_comment_count   TEXT,
    parent_comment_id   VARCHAR(255),
    like_count          TEXT,
    pictures            TEXT,
    brand_name          VARCHAR(128),
    INDEX idx_dy_cmt_id (comment_id),
    INDEX idx_dy_cmt_aweme (aweme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抖音评论';

-- -----------------------------------------------------------
-- B站
-- -----------------------------------------------------------
CREATE TABLE bilibili_video (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id            BIGINT NOT NULL,
    video_url           TEXT NOT NULL,
    user_id             BIGINT,
    nickname            TEXT,
    avatar              TEXT,
    liked_count         INT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    video_type          TEXT,
    title               TEXT,
    `desc`              TEXT,
    create_time         BIGINT COMMENT '秒时间戳',
    disliked_count      TEXT,
    video_play_count    TEXT,
    video_favorite_count TEXT,
    video_share_count   TEXT,
    video_coin_count    TEXT,
    video_danmaku       TEXT,
    video_comment       TEXT,
    video_cover_url     TEXT,
    source_keyword      TEXT,
    brand_name          VARCHAR(128),
    UNIQUE KEY uk_bili_video (video_id),
    INDEX idx_bili_user (user_id),
    INDEX idx_bili_create_time (create_time),
    INDEX idx_bili_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B站视频';

CREATE TABLE bilibili_video_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(255),
    nickname            TEXT,
    sex                 TEXT,
    sign                TEXT,
    avatar              TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    comment_id          BIGINT,
    video_id            BIGINT,
    content             TEXT,
    create_time         BIGINT,
    sub_comment_count   TEXT,
    parent_comment_id   VARCHAR(255),
    like_count          TEXT,
    brand_name          VARCHAR(128),
    INDEX idx_bili_cmt_id (comment_id),
    INDEX idx_bili_cmt_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B站评论';

-- -----------------------------------------------------------
-- 微博
-- -----------------------------------------------------------
CREATE TABLE weibo_note (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(255),
    nickname            TEXT,
    avatar              TEXT,
    gender              TEXT,
    profile_url         TEXT,
    ip_location         TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    note_id             BIGINT,
    content             TEXT,
    create_time         BIGINT COMMENT '秒时间戳',
    create_date_time    VARCHAR(255),
    liked_count         TEXT,
    comments_count      TEXT,
    shared_count        TEXT,
    note_url            TEXT,
    source_keyword      TEXT,
    brand_name          VARCHAR(128),
    INDEX idx_wb_note_id (note_id),
    INDEX idx_wb_create_time (create_time),
    INDEX idx_wb_create_date (create_date_time),
    INDEX idx_wb_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博';

CREATE TABLE weibo_note_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(255),
    nickname            TEXT,
    avatar              TEXT,
    gender              TEXT,
    profile_url         TEXT,
    ip_location         TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    comment_id          BIGINT,
    note_id             BIGINT,
    content             TEXT,
    create_time         BIGINT,
    create_date_time    VARCHAR(255),
    comment_like_count  TEXT,
    sub_comment_count   TEXT,
    parent_comment_id   VARCHAR(255),
    brand_name          VARCHAR(128),
    INDEX idx_wb_cmt_id (comment_id),
    INDEX idx_wb_cmt_note (note_id),
    INDEX idx_wb_cmt_date (create_date_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博评论';

-- -----------------------------------------------------------
-- 快手
-- -----------------------------------------------------------
CREATE TABLE kuaishou_video (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(64),
    nickname            TEXT,
    avatar              TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    video_id            VARCHAR(255),
    video_type          TEXT,
    title               TEXT,
    `desc`              TEXT,
    create_time         BIGINT COMMENT '毫秒时间戳',
    liked_count         TEXT,
    viewd_count         TEXT,
    video_url           TEXT,
    video_cover_url     TEXT,
    video_play_url      TEXT,
    source_keyword      TEXT,
    brand_name          VARCHAR(128),
    INDEX idx_ks_video_id (video_id),
    INDEX idx_ks_create_time (create_time),
    INDEX idx_ks_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快手视频';

CREATE TABLE kuaishou_video_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             TEXT,
    nickname            TEXT,
    avatar              TEXT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    comment_id          BIGINT,
    video_id            VARCHAR(255),
    content             TEXT,
    create_time         BIGINT,
    sub_comment_count   TEXT,
    brand_name          VARCHAR(128),
    INDEX idx_ks_cmt_id (comment_id),
    INDEX idx_ks_cmt_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快手评论';
