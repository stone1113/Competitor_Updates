-- ============================================================
-- migration_chat_v95.sql
-- v9.5 对话式深度对标：会话 + 消息双表
-- ============================================================

-- 1) 会话表（一次对标 = 一个 session，可多轮追问）
CREATE TABLE IF NOT EXISTS deep_benchmark_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_token VARCHAR(64) NOT NULL COMMENT '用户隔离 token；v9.5 用浏览器 localStorage UUID，v9.6 切飞书 user_id',
  feishu_user_id VARCHAR(64) DEFAULT NULL COMMENT 'v9.6 飞书登录后填充',
  title VARCHAR(255) NOT NULL COMMENT '自动从首条 user message 截 30 字',
  self_model VARCHAR(64) NOT NULL,
  competitor_models VARCHAR(255) NOT NULL COMMENT '逗号分隔',
  add_ts BIGINT NOT NULL,
  last_active_ts BIGINT NOT NULL,
  KEY idx_user_token (user_token, last_active_ts DESC),
  KEY idx_feishu_user (feishu_user_id, last_active_ts DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v9.5 深度对标会话';

-- 2) 消息表（user / assistant / system 通用）
CREATE TABLE IF NOT EXISTS deep_benchmark_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL COMMENT 'user | assistant | system',
  content MEDIUMTEXT NOT NULL COMMENT 'markdown 或 plain text',
  metadata JSON DEFAULT NULL COMMENT '{analystResults, chartData, agent_type, elapsedMs, ...}',
  add_ts BIGINT NOT NULL,
  KEY idx_session (session_id, add_ts ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v9.5 深度对标会话消息';
