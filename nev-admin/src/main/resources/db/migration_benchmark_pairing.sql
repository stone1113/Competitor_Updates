-- ============================================================
-- migration_benchmark_pairing.sql
-- 给 autohome_series_config 加 vs_self_model 字段表达对标关系：
--   猛士M817 vs 坦克400 / 方程豹豹5 / 问界M8
--   猛士917  vs 仰望U8 / 坦克700  / 卫士110 (路虎 Defender 110)
-- ============================================================

-- 1. 加字段
ALTER TABLE autohome_series_config
  ADD COLUMN vs_self_model VARCHAR(64) DEFAULT NULL
  COMMENT '主对标关系：填本品车型名（如「猛士M817」「猛士917」）；NULL 表示仅追踪不入对标矩阵'
  AFTER role;

ALTER TABLE autohome_series_config
  ADD INDEX idx_vs_self_model (vs_self_model);

-- 2. 修正仰望U8 brand_name（之前误录为「比亚迪」）
UPDATE autohome_series_config
SET brand_name = '仰望'
WHERE model_name = '仰望U8';

-- 3. 已有 4 款打对标标签
UPDATE autohome_series_config SET vs_self_model = '猛士M817' WHERE model_name = '方程豹豹5';
UPDATE autohome_series_config SET vs_self_model = '猛士M817' WHERE model_name = '问界M8';
UPDATE autohome_series_config SET vs_self_model = '猛士917'  WHERE model_name = '仰望U8';
UPDATE autohome_series_config SET vs_self_model = '猛士917'  WHERE model_name = '坦克700';

-- 4. 新增 2 款（series_id 占位，需用户在汽车之家 URL 里确认真实值后从前端改）
--    坦克400 ：长城 2026 款 Hi4-T 越野 SUV（autohome 上市可能尚未配置）
--    卫士110 ：路虎 Defender 110 老牌硬派越野
INSERT IGNORE INTO autohome_series_config
  (brand_name, model_name, series_id, role, vs_self_model, is_enabled, remark, add_ts, last_modify_ts)
VALUES
  ('坦克', '坦克400', '0000', 'competitor', '猛士M817',
   'series_id 待确认（汽车之家 URL 数字）', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('路虎', '卫士110', '0001', 'competitor', '猛士917',
   'series_id 待确认（路虎 Defender 110）', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);

-- 5. 同步给关键词中心（让 Bocha 也能抓这两款的新闻）
INSERT IGNORE INTO brand_keyword_config (brand_name, keyword, category, enabled, remark, add_ts, last_modify_ts) VALUES
('坦克', '坦克400', 'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('路虎', '卫士110', 'competitor', 1, NULL, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
('路虎', '路虎卫士', 'competitor', 1, '卫士车系总称', UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
