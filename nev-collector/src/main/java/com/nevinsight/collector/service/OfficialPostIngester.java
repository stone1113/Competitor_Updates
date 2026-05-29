package com.nevinsight.collector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nevinsight.model.entity.core.OfficialAccountConfig;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.OfficialAccountConfigMapper;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/**
 * 把官方账号已落库的帖（weibo_note / douyin_aweme / xhs_note）ingest 到 web_search_news，
 * 让事件分类管线（PR11）自动处理。
 *
 * 触发时机：OfficialAccountCrawlerService 抓取后 5-10 分钟（crawler-service 子进程异步写入需要时间）。
 *
 * 去重：基于 url_hash（SHA256(原帖 URL)）。重复 url 已存在则跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialPostIngester {

    /** ingest 回看窗口（毫秒）—— 跟 CompetitorReportPipeline 36h 对齐，避免日报漏官号一手数据。
     *  url_hash 唯一约束保证重复 insert 不会双入库。 */
    private static final long LOOKBACK_MS = 36L * 3600 * 1000;

    private final OfficialAccountConfigMapper configMapper;
    private final WebSearchNewsMapper newsMapper;
    private final JdbcTemplate jdbcTemplate;

    public IngestResult ingestAll() {
        List<OfficialAccountConfig> accounts = configMapper.selectList(
                new LambdaQueryWrapper<OfficialAccountConfig>()
                        .eq(OfficialAccountConfig::getIsEnabled, true));
        if (accounts.isEmpty()) {
            return new IngestResult(0, 0);
        }

        long sinceMs = System.currentTimeMillis() - LOOKBACK_MS;
        int totalScanned = 0;
        int totalInserted = 0;

        // 按 platform 分组
        Map<String, List<OfficialAccountConfig>> byPlatform = new LinkedHashMap<>();
        for (OfficialAccountConfig c : accounts) {
            byPlatform.computeIfAbsent(c.getPlatform(), k -> new ArrayList<>()).add(c);
        }

        for (Map.Entry<String, List<OfficialAccountConfig>> e : byPlatform.entrySet()) {
            String platform = e.getKey();
            List<OfficialAccountConfig> grp = e.getValue();
            for (OfficialAccountConfig c : grp) {
                try {
                    int[] r = ingestOne(platform, c, sinceMs);
                    totalScanned += r[0];
                    totalInserted += r[1];
                } catch (Exception ex) {
                    log.error("[OfficialIngest] {} {} failed: {}",
                            platform, c.getAccountId(), ex.getMessage());
                }
            }
        }

        log.info("[OfficialIngest] done scanned={} inserted={}", totalScanned, totalInserted);
        return new IngestResult(totalScanned, totalInserted);
    }

    /** @return int[2]{scanned, inserted} */
    private int[] ingestOne(String platform, OfficialAccountConfig cfg, long sinceMs) {
        List<Map<String, Object>> rows;
        String sourceTool;
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();

        // sinceMs 用作"发布时间"过滤上限：36h 内发布的官号帖
        long sinceSec = sinceMs / 1000;
        switch (platform) {
            case "wb":
                sourceTool = "weibo_official";
                // weibo_note.create_time 是 unix 秒，真实发布时间；pics 是 v8 加的图片 URL 字段
                rows = jdbcTemplate.queryForList(
                        "SELECT note_id, content, note_url, pics, create_time AS publish_sec " +
                        "FROM weibo_note WHERE user_id = ? AND create_time >= ? " +
                        "ORDER BY create_time DESC LIMIT 50",
                        cfg.getAccountId(), sinceSec);
                break;
            case "dy":
                sourceTool = "douyin_official";
                // douyin_aweme.create_time 是 unix 秒；dy 暂无 pics 字段（v8 仅做微博）
                rows = jdbcTemplate.queryForList(
                        "SELECT aweme_id AS note_id, `desc` AS content, aweme_url AS note_url, " +
                        "NULL AS pics, create_time AS publish_sec " +
                        "FROM douyin_aweme WHERE sec_uid = ? AND create_time >= ? " +
                        "ORDER BY create_time DESC LIMIT 50",
                        cfg.getAccountId(), sinceSec);
                break;
            case "xhs":
                sourceTool = "xhs_official";
                // xhs_note.time 通常是 unix 毫秒
                rows = jdbcTemplate.queryForList(
                        "SELECT note_id, `desc` AS content, note_url, NULL AS pics, `time` AS publish_sec " +
                        "FROM xhs_note WHERE user_id = ? AND `time` >= ? " +
                        "ORDER BY `time` DESC LIMIT 50",
                        cfg.getAccountId(), sinceMs);  // xhs 用毫秒
                break;
            default:
                return new int[]{0, 0};
        }

        int scanned = rows.size();
        int inserted = 0;
        for (Map<String, Object> r : rows) {
            String content = String.valueOf(r.getOrDefault("content", ""));
            if (content == null || content.isEmpty() || "null".equals(content)) continue;

            String url = String.valueOf(r.getOrDefault("note_url", ""));
            if (url == null || url.isEmpty() || "null".equals(url)) {
                // 兜底用 note_id 当 hash 输入
                url = platform + ":" + String.valueOf(r.get("note_id"));
            }
            String urlHash = sha256(url);

            Long exists = newsMapper.selectCount(
                    new QueryWrapper<WebSearchNews>().eq("url_hash", urlHash));
            if (exists != null && exists > 0) continue;

            // 真实发布时间（秒级，weibo/douyin 是 unix 秒；xhs 已经是毫秒，要换算）
            Object pubObj = r.get("publish_sec");
            long publishMs;
            if (pubObj == null) {
                publishMs = now;
            } else {
                long v = ((Number) pubObj).longValue();
                publishMs = ("xhs".equals(platform)) ? v : v * 1000L;
            }

            // v8: 透传图片 URL（仅微博有，其他渠道 pics 是 NULL）
            Object picsObj = r.get("pics");
            String imageUrls = picsObj == null ? null : String.valueOf(picsObj);
            if (imageUrls != null && (imageUrls.isEmpty() || "null".equals(imageUrls))) {
                imageUrls = null;
            }

            WebSearchNews news = new WebSearchNews();
            news.setUrlHash(urlHash);
            news.setSourceTool(sourceTool);
            news.setSearchQuery("@" + (cfg.getAccountName() == null ? cfg.getAccountId() : cfg.getAccountName()));
            news.setBrandName(cfg.getBrandName());
            news.setCategory("competitor");  // 后续可按 brand 关键词反查 self/competitor
            news.setTitle(truncate(content, 250));
            news.setUrl(url);
            news.setContent(content);
            news.setImageUrls(imageUrls);
            news.setPublishedDate(null);
            news.setCrawlDate(today);
            // 关键：把 add_ts 写成发布时间毫秒，让下游 36h 窗口按发布时间过滤
            // （MyBatis-Plus MetaHandler 仅在 add_ts 为 null 时填充，手动设值不会被覆盖）
            news.setAddTs(publishMs);
            news.setLastModifyTs(now);
            // event_type/extract_ts 留空，让 EventExtractionScheduler 自动跑

            try {
                newsMapper.insert(news);
                inserted++;
            } catch (Exception e) {
                log.debug("[OfficialIngest] dup or err url_hash={}: {}", urlHash, e.getMessage());
            }
        }
        log.info("[OfficialIngest] {} {} ({}) → scanned={} inserted={}",
                platform, cfg.getBrandName(), cfg.getAccountName(), scanned, inserted);
        return new int[]{scanned, inserted};
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    public static class IngestResult {
        public final int scanned;
        public final int inserted;

        public IngestResult(int scanned, int inserted) {
            this.scanned = scanned;
            this.inserted = inserted;
        }
    }
}
