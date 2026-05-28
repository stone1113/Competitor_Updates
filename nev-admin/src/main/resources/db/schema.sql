-- ============================================================
-- NEV-Insight 舆情监控与销售赋能系统 - TDSQL (MySQL模式) DDL
-- ============================================================

-- -----------------------------------------------------------
-- 1. 核心业务表 (从 BettaFish 迁移，PostgreSQL -> MySQL 语法)
-- -----------------------------------------------------------

CREATE TABLE IF NOT EXISTS daily_sentiment_summary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name      VARCHAR(128) NOT NULL,
    report_date     DATE NOT NULL,
    total_mentions  INT DEFAULT 0,
    total_engagement BIGINT DEFAULT 0,
    very_positive_count INT DEFAULT 0,
    positive_count      INT DEFAULT 0,
    neutral_count       INT DEFAULT 0,
    negative_count      INT DEFAULT 0,
    very_negative_count INT DEFAULT 0,
    avg_sentiment_score DOUBLE DEFAULT 0,
    risk_alert_count    INT DEFAULT 0,
    negative_ratio      DOUBLE DEFAULT 0,
    platform_breakdown  TEXT COMMENT 'JSON: 平台分布',
    add_ts              BIGINT NOT NULL,
    last_modify_ts      BIGINT NOT NULL,
    UNIQUE KEY uk_brand_date (brand_name, report_date),
    INDEX idx_brand (brand_name),
    INDEX idx_date (report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日情感摘要';

CREATE TABLE IF NOT EXISTS web_search_news (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_hash        VARCHAR(64) NOT NULL,
    source_tool     VARCHAR(32) NOT NULL COMMENT 'bocha/tavily/anspire',
    search_query    VARCHAR(500) NOT NULL,
    brand_name      VARCHAR(128) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    url             VARCHAR(1024),
    content         MEDIUMTEXT,
    published_date  VARCHAR(64),
    relevance_score DOUBLE,
    crawl_date      DATE NOT NULL,
    add_ts          BIGINT NOT NULL,
    last_modify_ts  BIGINT NOT NULL,
    UNIQUE KEY uk_url_hash (url_hash),
    INDEX idx_brand_date (brand_name, crawl_date),
    INDEX idx_crawl_date (crawl_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索引擎新闻';

CREATE TABLE IF NOT EXISTS daily_report_record (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name          VARCHAR(128) NOT NULL,
    report_date         DATE NOT NULL,
    card_json           LONGTEXT NOT NULL,
    sections_json       LONGTEXT NOT NULL,
    kpi_json            TEXT,
    build_context_json  LONGTEXT,
    status              VARCHAR(32) DEFAULT 'draft' COMMENT 'draft/test_pushed/prod_pushed',
    pushed_test_ts      BIGINT,
    pushed_prod_ts      BIGINT,
    test_push_count     INT DEFAULT 0,
    prod_push_count     INT DEFAULT 0,
    last_error          TEXT,
    add_ts              BIGINT NOT NULL,
    last_modify_ts      BIGINT NOT NULL,
    UNIQUE KEY uk_brand_date (brand_name, report_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日报记录';

-- -----------------------------------------------------------
-- 2. 社交平台内容表 (从 BettaFish 迁移)
-- -----------------------------------------------------------

CREATE TABLE IF NOT EXISTS xhs_note (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name      VARCHAR(128),
    note_id         VARCHAR(128),
    title           VARCHAR(500),
    `desc`          TEXT,
    note_url        VARCHAR(1024),
    nickname        VARCHAR(128),
    user_id         VARCHAR(128),
    liked_count     VARCHAR(32),
    comment_count   VARCHAR(32),
    share_count     VARCHAR(32),
    collected_count VARCHAR(32),
    `time`          BIGINT COMMENT '毫秒时间戳',
    type            VARCHAR(32),
    image_list      TEXT,
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    INDEX idx_brand_time (brand_name, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小红书笔记';

CREATE TABLE IF NOT EXISTS douyin_aweme (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name      VARCHAR(128),
    aweme_id        BIGINT,
    title           VARCHAR(500),
    `desc`          TEXT,
    aweme_url       VARCHAR(1024),
    nickname        VARCHAR(128),
    user_id         VARCHAR(128),
    liked_count     VARCHAR(32),
    comment_count   VARCHAR(32),
    share_count     VARCHAR(32),
    collected_count VARCHAR(32),
    create_time     BIGINT COMMENT '秒时间戳',
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    INDEX idx_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抖音视频';

CREATE TABLE IF NOT EXISTS bilibili_video (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name           VARCHAR(128),
    video_id             BIGINT,
    title                VARCHAR(500),
    `desc`               TEXT,
    video_url            VARCHAR(1024),
    nickname             VARCHAR(128),
    user_id              VARCHAR(128),
    liked_count          INT DEFAULT 0,
    video_comment        VARCHAR(32),
    video_share_count    VARCHAR(32),
    video_play_count     VARCHAR(32),
    video_favorite_count VARCHAR(32),
    video_danmaku        VARCHAR(32),
    create_time          BIGINT COMMENT '秒时间戳',
    add_ts               BIGINT,
    last_modify_ts       BIGINT,
    INDEX idx_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B站视频';

CREATE TABLE IF NOT EXISTS weibo_note (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name      VARCHAR(128),
    note_id         BIGINT,
    content         TEXT,
    note_url        VARCHAR(1024),
    nickname        VARCHAR(128),
    user_id         VARCHAR(128),
    liked_count     VARCHAR(32),
    comments_count  VARCHAR(32),
    shared_count    VARCHAR(32),
    create_time     BIGINT COMMENT '秒时间戳',
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    INDEX idx_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博';

CREATE TABLE IF NOT EXISTS kuaishou_video (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name      VARCHAR(128),
    video_id        VARCHAR(128),
    title           VARCHAR(500),
    `desc`          TEXT,
    video_url       VARCHAR(1024),
    nickname        VARCHAR(128),
    user_id         VARCHAR(128),
    liked_count     VARCHAR(32),
    viewd_count     VARCHAR(32),
    create_time     BIGINT COMMENT '毫秒时间戳',
    add_ts          BIGINT,
    last_modify_ts  BIGINT,
    INDEX idx_brand_time (brand_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快手视频';

-- -----------------------------------------------------------
-- 3. 社交平台评论表 (从 BettaFish 迁移)
-- -----------------------------------------------------------

CREATE TABLE IF NOT EXISTS xhs_note_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name          VARCHAR(128),
    note_id             VARCHAR(128),
    comment_id          VARCHAR(128),
    content             TEXT,
    nickname            VARCHAR(128),
    user_id             VARCHAR(128),
    like_count          VARCHAR(32),
    sub_comment_count   VARCHAR(32),
    `time`              BIGINT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    INDEX idx_note (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小红书评论';

CREATE TABLE IF NOT EXISTS douyin_aweme_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name          VARCHAR(128),
    aweme_id            VARCHAR(128),
    comment_id          VARCHAR(128),
    content             TEXT,
    nickname            VARCHAR(128),
    user_id             VARCHAR(128),
    like_count          VARCHAR(32),
    sub_comment_count   VARCHAR(32),
    create_time         BIGINT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    INDEX idx_aweme (aweme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抖音评论';

CREATE TABLE IF NOT EXISTS bilibili_video_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name          VARCHAR(128),
    video_id            VARCHAR(128),
    comment_id          VARCHAR(128),
    content             TEXT,
    nickname            VARCHAR(128),
    user_id             VARCHAR(128),
    like_count          VARCHAR(32),
    sub_comment_count   VARCHAR(32),
    create_time         BIGINT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    INDEX idx_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B站评论';

CREATE TABLE IF NOT EXISTS weibo_note_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name          VARCHAR(128),
    note_id             VARCHAR(128),
    comment_id          VARCHAR(128),
    content             TEXT,
    nickname            VARCHAR(128),
    user_id             VARCHAR(128),
    like_count          VARCHAR(32),
    sub_comment_count   VARCHAR(32),
    create_time         BIGINT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    INDEX idx_note (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博评论';

CREATE TABLE IF NOT EXISTS kuaishou_video_comment (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name          VARCHAR(128),
    video_id            VARCHAR(128),
    comment_id          VARCHAR(128),
    content             TEXT,
    nickname            VARCHAR(128),
    user_id             VARCHAR(128),
    like_count          VARCHAR(32),
    sub_comment_count   VARCHAR(32),
    create_time         BIGINT,
    add_ts              BIGINT,
    last_modify_ts      BIGINT,
    INDEX idx_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快手评论';

-- -----------------------------------------------------------
-- 4. 新增业务表
-- -----------------------------------------------------------

CREATE TABLE IF NOT EXISTS data_source_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_type     VARCHAR(32) NOT NULL COMMENT 'BOCHA_API/CRAWLER/RSS',
    source_name     VARCHAR(128) NOT NULL,
    brand_name      VARCHAR(64) NOT NULL,
    keywords        JSON NOT NULL COMMENT '关键词列表',
    cron_expression VARCHAR(64) COMMENT 'XXL-JOB cron表达式',
    is_enabled      TINYINT(1) DEFAULT 1,
    extra_config    JSON COMMENT '扩展配置',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_source_brand (source_type, source_name, brand_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源配置';

CREATE TABLE IF NOT EXISTS cleaning_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_type       VARCHAR(32) NOT NULL COMMENT 'SENSITIVE_WORD/SIMHASH_DEDUP/REGEX_FILTER',
    rule_name       VARCHAR(128) NOT NULL,
    rule_config     JSON NOT NULL COMMENT '规则配置',
    priority        INT DEFAULT 0,
    is_enabled      TINYINT(1) DEFAULT 1,
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清洗规则';

CREATE TABLE IF NOT EXISTS collector_task_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         VARCHAR(64) NOT NULL,
    source_type     VARCHAR(32) NOT NULL,
    brand_name      VARCHAR(64),
    status          VARCHAR(16) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    record_count    INT DEFAULT 0,
    error_message   TEXT,
    start_time      DATETIME,
    end_time        DATETIME,
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_status (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务日志';

CREATE TABLE IF NOT EXISTS knowledge_document (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_name        VARCHAR(256) NOT NULL,
    doc_type        VARCHAR(32) NOT NULL COMMENT 'EXCEL/PDF/MARKDOWN',
    file_path       VARCHAR(512),
    collection_name VARCHAR(128) NOT NULL COMMENT 'Milvus collection名',
    chunk_count     INT DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

CREATE TABLE IF NOT EXISTS talking_point_version (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    talking_point_id    BIGINT NOT NULL,
    version             INT NOT NULL,
    content             JSON NOT NULL COMMENT '话术快照',
    change_reason       VARCHAR(256),
    author              VARCHAR(64) DEFAULT 'system',
    is_current          TINYINT(1) DEFAULT 0,
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tp_version (talking_point_id, version),
    INDEX idx_tp_current (talking_point_id, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话术版本';

CREATE TABLE IF NOT EXISTS prompt_template (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_name          VARCHAR(64) NOT NULL COMMENT 'SENTIMENT_AGENT/COMPETITOR_AGENT/PITCH_AGENT/ASSEMBLY_AGENT',
    template_name       VARCHAR(128) NOT NULL,
    template_content    TEXT NOT NULL,
    variables           JSON COMMENT '模板变量列表',
    version             INT DEFAULT 1,
    is_active           TINYINT(1) DEFAULT 1,
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_template (agent_name, template_name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt模板';

CREATE TABLE IF NOT EXISTS competitor_dimension_score (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name      VARCHAR(64) NOT NULL,
    report_date     DATE NOT NULL,
    dimension       VARCHAR(64) NOT NULL COMMENT '续航/智驾/底盘/动力/安全/内饰/空间/价格/口碑/服务/智能化/设计',
    score           DECIMAL(5,2),
    evidence        TEXT,
    source          VARCHAR(32) COMMENT 'LLM/MANUAL/CRAWLED',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_date_dim (brand_name, report_date, dimension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='竞品维度评分';

CREATE TABLE IF NOT EXISTS llm_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_name      VARCHAR(64) NOT NULL,
    model_name      VARCHAR(64) NOT NULL,
    prompt_hash     VARCHAR(64),
    input_tokens    INT,
    output_tokens   INT,
    latency_ms      INT,
    status          VARCHAR(16),
    error_message   TEXT,
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_agent (agent_name, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM调用审计';

CREATE TABLE IF NOT EXISTS crawler_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    remote_task_id  VARCHAR(64) COMMENT 'Python 爬虫服务返回的任务 UUID',
    brand_name      VARCHAR(128) NOT NULL,
    platform        VARCHAR(16) NOT NULL COMMENT 'xhs/dy/ks/bili/wb',
    keywords        VARCHAR(2048) NOT NULL COMMENT 'JSON 数组字符串',
    max_notes       INT NOT NULL DEFAULT 10,
    enable_comments TINYINT(1) NOT NULL DEFAULT 1,
    login_type      VARCHAR(16) NOT NULL DEFAULT 'cookie',
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
    started_at      BIGINT,
    finished_at     BIGINT,
    duration_sec    INT,
    error_message   VARCHAR(2048),
    add_ts          BIGINT NOT NULL,
    last_modify_ts  BIGINT NOT NULL,
    UNIQUE KEY uk_remote (remote_task_id),
    INDEX idx_brand_platform (brand_name, platform, add_ts),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社交平台爬虫任务记录';
