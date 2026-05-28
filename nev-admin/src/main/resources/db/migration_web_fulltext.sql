-- ============================================================
-- migration_web_fulltext.sql
-- Bocha 网页正文补采：搜索 snippet 升级为网页全文
-- ============================================================

ALTER TABLE web_search_news
  MODIFY COLUMN content MEDIUMTEXT NULL;
