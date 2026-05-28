-- ============================================================
-- migration_v8_ocr.sql
-- v8: 官号微博图片采集 + 通义千问视觉模型 OCR 提取优惠政策
-- ============================================================

-- 1) weibo_note 加图片 URL 字段（MediaCrawler 之前只下载本地，现在入库）
ALTER TABLE weibo_note
  ADD COLUMN pics TEXT DEFAULT NULL COMMENT '图片 URL 逗号分隔（mblog.pics 提取，最多 9 张）'
  AFTER content;

-- 2) web_search_news 加图片透传 + OCR 提取字段
ALTER TABLE web_search_news
  ADD COLUMN image_urls TEXT DEFAULT NULL
    COMMENT '图片 URL 逗号分隔（仅官号源传递）' AFTER content,
  ADD COLUMN image_ocr_text TEXT DEFAULT NULL
    COMMENT 'qwen-vl 提取的图片文字 + 政策摘要' AFTER image_urls,
  ADD COLUMN ocr_ts BIGINT DEFAULT NULL
    COMMENT 'OCR 完成时间 (毫秒)' AFTER image_ocr_text,
  ADD INDEX idx_ocr_pending (event_type, ocr_ts);
