-- 单例配置表：爬虫全量定时任务的 cron 和启停
CREATE TABLE IF NOT EXISTS crawler_schedule_config (
    id              INT PRIMARY KEY,
    enabled         TINYINT(1) NOT NULL DEFAULT 0,
    cron            VARCHAR(64) NOT NULL DEFAULT '0 0 2 * * ?',
    last_modify_ts  BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫定时配置（单例）';

INSERT INTO crawler_schedule_config (id, enabled, cron, last_modify_ts)
VALUES (1, 0, '0 0 2 * * ?', UNIX_TIMESTAMP()*1000)
ON DUPLICATE KEY UPDATE id=id;
