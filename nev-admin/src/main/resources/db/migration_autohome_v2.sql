-- ============================================================
-- migration_autohome_v2.sql
-- 扩 seed 到约 20 款（v1 已有 8 款，新增 12 款）
-- series_id 是占位（基于汽车之家常见车型 ID，运行时如抓不到会标 failed）
-- 注：跑完抓取后用户可在 /competitor/autohome-config 页面修正具体 series_id
-- ============================================================
INSERT IGNORE INTO autohome_series_config
  (brand_name, model_name, series_id, role, is_enabled, add_ts, last_modify_ts)
VALUES
  ('仰望',   '仰望U9',       '7400', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('方程豹', '方程豹豹8',    '7500', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('坦克',   '坦克300',      '4666', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('坦克',   '坦克700',      '7100', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('问界',   '问界M7',       '6177', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('问界',   '问界M8',       '7300', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('理想',   '理想L7',       '5985', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('理想',   '理想L8',       '5987', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('理想',   '理想MEGA',     '6822', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('智界',   '智界R7',       '7000', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('阿维塔', '阿维塔07',     '6900', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000),
  ('阿维塔', '阿维塔12',     '6600', 'competitor', 1, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000);
