package com.nevinsight.collector.service;

import com.nevinsight.model.dto.response.BuzzItemResponse;
import com.nevinsight.model.dto.response.CommentItemResponse;
import com.nevinsight.model.dto.response.ContentPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformQueryService {

    private final JdbcTemplate jdbcTemplate;

    /** 日报场景：取过去 36 小时<b>采集到</b>的内容（按 add_ts 过滤）。
     *  社交内容大多是历史热点，按"内容发布日期"切片会拿不到数据，必须按"我们什么时候采到的"。
     *  date 参数：取该日 23:59:59 为窗口右端，向前推 36h；传 null 用 now。 */
    private static final int BUZZ_COLLECTION_WINDOW_HOURS = 36;

    /**
     * 构建跨平台 UNION ALL 查询 (MySQL/TDSQL 语法)。
     * 时间窗：基于 add_ts（采集毫秒时间戳，所有平台表都有）做过滤。
     */
    public List<BuzzItemResponse> queryBuzzUnion(String brand, LocalDate date, String platform, int limit, int offset) {
        long endMs = (date != null
                ? date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
                : System.currentTimeMillis());
        long startMs = endMs - BUZZ_COLLECTION_WINDOW_HOURS * 3600_000L;
        // 第三、四参数（sec）已不再使用（SQL 改为按 add_ts 过滤），保留签名兼容
        String sql = buildBuzzUnionSql(brand, platform, startMs, endMs, 0L, 0L);
        sql += " ORDER BY engagement_score DESC LIMIT " + limit + " OFFSET " + offset;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<BuzzItemResponse> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            results.add(BuzzItemResponse.builder()
                    .platform(str(row, "platform"))
                    .platformName(str(row, "platform_name"))
                    .contentId(str(row, "content_id"))
                    .title(truncate(str(row, "title"), 100))
                    .content(truncate(str(row, "content"), 200))
                    .url(str(row, "url"))
                    .nickname(str(row, "nickname"))
                    .likedCount(str(row, "liked_count"))
                    .commentCount(str(row, "comment_count"))
                    .shareCount(str(row, "share_count"))
                    .viewCount(str(row, "view_count"))
                    .collectedCount(str(row, "collected_count"))
                    .time(toLong(row.get("time_val")))
                    .engagementScore(toLong(row.get("engagement_score")))
                    .build());
        }
        return results;
    }

    /**
     * 构建 UNION ALL SQL - MySQL/TDSQL 语法
     * 关键转换: PostgreSQL ~ -> REGEXP, CAST AS bigint -> CAST AS SIGNED
     */
    private String buildBuzzUnionSql(String brand, String platform,
                                     long dayStartMs, long dayEndMs,
                                     long dayStartSec, long dayEndSec) {
        List<String> parts = new ArrayList<>();
        boolean all = platform == null || platform.isEmpty();
        // 发布时间窗（用于过滤帖子自身的发布时间，避免拿到很久以前发的、近期才被采集的旧帖）
        // xhs.time / douyin.create_time 等单位不同，分别按毫秒/秒生成
        long pubStartMs = dayStartMs;
        long pubEndMs = dayEndMs;
        long pubStartSec = dayStartMs / 1000;
        long pubEndSec = dayEndMs / 1000;

        if (all || "xhs".equals(platform)) {
            parts.add(String.format(
                "(SELECT 'xhs' AS platform, '小红书' AS platform_name, note_id AS content_id, " +
                "title, `desc` AS content, note_url AS url, nickname, " +
                "liked_count, comment_count, share_count, '0' AS view_count, collected_count, " +
                "`time` AS time_val, " +
                "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                "+ CASE WHEN comment_count REGEXP '^[0-9]+$' THEN CAST(comment_count AS SIGNED) ELSE 0 END * 5 " +
                "+ CASE WHEN share_count REGEXP '^[0-9]+$' THEN CAST(share_count AS SIGNED) ELSE 0 END * 10 " +
                "+ CASE WHEN collected_count REGEXP '^[0-9]+$' THEN CAST(collected_count AS SIGNED) ELSE 0 END * 2 " +
                ") AS engagement_score, " +
                "CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END AS liked_count_num, " +
                "CASE WHEN comment_count REGEXP '^[0-9]+$' THEN CAST(comment_count AS SIGNED) ELSE 0 END AS comment_count_num " +
                "FROM xhs_note WHERE brand_name = '%s' AND add_ts >= %d AND add_ts < %d " +
                "AND `time` >= %d AND `time` < %d)",
                brand, dayStartMs, dayEndMs, pubStartMs, pubEndMs));
        }

        if (all || "dy".equals(platform)) {
            parts.add(String.format(
                "(SELECT 'dy' AS platform, '抖音' AS platform_name, CAST(aweme_id AS CHAR) AS content_id, " +
                "title, `desc` AS content, aweme_url AS url, nickname, " +
                "liked_count, comment_count, share_count, '0' AS view_count, collected_count, " +
                "create_time AS time_val, " +
                "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                "+ CASE WHEN comment_count REGEXP '^[0-9]+$' THEN CAST(comment_count AS SIGNED) ELSE 0 END * 5 " +
                "+ CASE WHEN share_count REGEXP '^[0-9]+$' THEN CAST(share_count AS SIGNED) ELSE 0 END * 10 " +
                "+ CASE WHEN collected_count REGEXP '^[0-9]+$' THEN CAST(collected_count AS SIGNED) ELSE 0 END * 2 " +
                ") AS engagement_score, " +
                "CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END AS liked_count_num, " +
                "CASE WHEN comment_count REGEXP '^[0-9]+$' THEN CAST(comment_count AS SIGNED) ELSE 0 END AS comment_count_num " +
                "FROM douyin_aweme WHERE brand_name = '%s' AND add_ts >= %d AND add_ts < %d " +
                "AND create_time >= %d AND create_time < %d)",
                brand, dayStartMs, dayEndMs, pubStartSec, pubEndSec));
        }

        if (all || "bili".equals(platform)) {
            parts.add(String.format(
                "(SELECT 'bili' AS platform, 'B站' AS platform_name, CAST(video_id AS CHAR) AS content_id, " +
                "title, `desc` AS content, video_url AS url, nickname, " +
                "CAST(COALESCE(liked_count, 0) AS CHAR) AS liked_count, video_comment AS comment_count, " +
                "video_share_count AS share_count, video_play_count AS view_count, video_favorite_count AS collected_count, " +
                "create_time AS time_val, " +
                "( COALESCE(liked_count, 0) * 1 " +
                "+ CASE WHEN video_comment REGEXP '^[0-9]+$' THEN CAST(video_comment AS SIGNED) ELSE 0 END * 5 " +
                "+ CASE WHEN video_share_count REGEXP '^[0-9]+$' THEN CAST(video_share_count AS SIGNED) ELSE 0 END * 10 " +
                "+ CASE WHEN video_play_count REGEXP '^[0-9]+$' THEN CAST(video_play_count AS SIGNED) ELSE 0 END / 10 " +
                "+ CASE WHEN video_favorite_count REGEXP '^[0-9]+$' THEN CAST(video_favorite_count AS SIGNED) ELSE 0 END * 2 " +
                ") AS engagement_score, " +
                "CAST(COALESCE(liked_count, 0) AS SIGNED) AS liked_count_num, " +
                "CASE WHEN video_comment REGEXP '^[0-9]+$' THEN CAST(video_comment AS SIGNED) ELSE 0 END AS comment_count_num " +
                "FROM bilibili_video WHERE brand_name = '%s' AND add_ts >= %d AND add_ts < %d " +
                "AND create_time >= %d AND create_time < %d)",
                brand, dayStartMs, dayEndMs, pubStartSec, pubEndSec));
        }

        if (all || "wb".equals(platform)) {
            parts.add(String.format(
                "(SELECT 'wb' AS platform, '微博' AS platform_name, CAST(note_id AS CHAR) AS content_id, " +
                "content AS title, content, note_url AS url, nickname, " +
                "liked_count, comments_count AS comment_count, shared_count AS share_count, " +
                "'0' AS view_count, '0' AS collected_count, " +
                "create_time AS time_val, " +
                "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                "+ CASE WHEN comments_count REGEXP '^[0-9]+$' THEN CAST(comments_count AS SIGNED) ELSE 0 END * 5 " +
                "+ CASE WHEN shared_count REGEXP '^[0-9]+$' THEN CAST(shared_count AS SIGNED) ELSE 0 END * 10 " +
                ") AS engagement_score, " +
                "CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END AS liked_count_num, " +
                "CASE WHEN comments_count REGEXP '^[0-9]+$' THEN CAST(comments_count AS SIGNED) ELSE 0 END AS comment_count_num " +
                "FROM weibo_note WHERE brand_name = '%s' AND add_ts >= %d AND add_ts < %d " +
                "AND create_time >= %d AND create_time < %d)",
                brand, dayStartMs, dayEndMs, pubStartSec, pubEndSec));
        }

        if (all || "ks".equals(platform)) {
            parts.add(String.format(
                "(SELECT 'ks' AS platform, '快手' AS platform_name, video_id AS content_id, " +
                "title, `desc` AS content, video_url AS url, nickname, " +
                "liked_count, '0' AS comment_count, '0' AS share_count, " +
                "viewd_count AS view_count, '0' AS collected_count, " +
                "create_time AS time_val, " +
                "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                "+ CASE WHEN viewd_count REGEXP '^[0-9]+$' THEN CAST(viewd_count AS SIGNED) ELSE 0 END / 10 " +
                ") AS engagement_score, " +
                "CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END AS liked_count_num, " +
                "CAST(0 AS SIGNED) AS comment_count_num " +
                "FROM kuaishou_video WHERE brand_name = '%s' AND add_ts >= %d AND add_ts < %d " +
                "AND create_time >= %d AND create_time < %d)",
                brand, dayStartMs, dayEndMs, pubStartSec, pubEndSec));
        }

        if (parts.isEmpty()) {
            return "SELECT 'none' AS platform, '' AS platform_name, '' AS content_id, " +
                   "'' AS title, '' AS content, '' AS url, '' AS nickname, " +
                   "'0' AS liked_count, '0' AS comment_count, '0' AS share_count, " +
                   "'0' AS view_count, '0' AS collected_count, " +
                   "0 AS time_val, 0 AS engagement_score, " +
                   "0 AS liked_count_num, 0 AS comment_count_num WHERE 1=0";
        }

        return String.join(" UNION ALL ", parts);
    }

    private String str(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : "";
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0L; }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ===== 新增：可过滤的 UNION ALL 查询 =====

    private static final List<String> ALL_PLATFORMS = Arrays.asList("xhs", "dy", "bili", "wb", "ks");

    /** 各平台的 SELECT/WHERE 元数据 */
    private static class PlatformMeta {
        final String tableName, idCast, titleExpr, contentExpr, urlCol, nicknameCol, timeCol;
        final boolean timeInMs;
        final String platformLabel, platformCode;
        final String engagementExpr, likedSelect, commentSelect, shareSelect, viewSelect, collectedSelect;

        PlatformMeta(String code, String label, String table, String idCast,
                     String titleExpr, String contentExpr, String urlCol, String nicknameCol,
                     String timeCol, boolean timeInMs,
                     String likedSelect, String commentSelect, String shareSelect,
                     String viewSelect, String collectedSelect, String engagementExpr) {
            this.platformCode = code; this.platformLabel = label;
            this.tableName = table; this.idCast = idCast;
            this.titleExpr = titleExpr; this.contentExpr = contentExpr;
            this.urlCol = urlCol; this.nicknameCol = nicknameCol;
            this.timeCol = timeCol; this.timeInMs = timeInMs;
            this.likedSelect = likedSelect; this.commentSelect = commentSelect;
            this.shareSelect = shareSelect; this.viewSelect = viewSelect;
            this.collectedSelect = collectedSelect;
            this.engagementExpr = engagementExpr;
        }
    }

    private PlatformMeta metaOf(String code) {
        switch (code) {
            case "xhs": return new PlatformMeta("xhs", "小红书", "xhs_note", "note_id",
                    "title", "`desc`", "note_url", "nickname", "`time`", true,
                    "liked_count", "comment_count", "share_count", "'0'", "collected_count",
                    "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                    "+ CASE WHEN comment_count REGEXP '^[0-9]+$' THEN CAST(comment_count AS SIGNED) ELSE 0 END * 5 " +
                    "+ CASE WHEN share_count REGEXP '^[0-9]+$' THEN CAST(share_count AS SIGNED) ELSE 0 END * 10 " +
                    "+ CASE WHEN collected_count REGEXP '^[0-9]+$' THEN CAST(collected_count AS SIGNED) ELSE 0 END * 2 )");
            case "dy": return new PlatformMeta("dy", "抖音", "douyin_aweme", "CAST(aweme_id AS CHAR)",
                    "title", "`desc`", "aweme_url", "nickname", "create_time", false,
                    "liked_count", "comment_count", "share_count", "'0'", "collected_count",
                    "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                    "+ CASE WHEN comment_count REGEXP '^[0-9]+$' THEN CAST(comment_count AS SIGNED) ELSE 0 END * 5 " +
                    "+ CASE WHEN share_count REGEXP '^[0-9]+$' THEN CAST(share_count AS SIGNED) ELSE 0 END * 10 " +
                    "+ CASE WHEN collected_count REGEXP '^[0-9]+$' THEN CAST(collected_count AS SIGNED) ELSE 0 END * 2 )");
            case "bili": return new PlatformMeta("bili", "B站", "bilibili_video", "CAST(video_id AS CHAR)",
                    "title", "`desc`", "video_url", "nickname", "create_time", false,
                    "CAST(COALESCE(liked_count, 0) AS CHAR)", "video_comment", "video_share_count",
                    "video_play_count", "video_favorite_count",
                    "( COALESCE(liked_count, 0) * 1 " +
                    "+ CASE WHEN video_comment REGEXP '^[0-9]+$' THEN CAST(video_comment AS SIGNED) ELSE 0 END * 5 " +
                    "+ CASE WHEN video_share_count REGEXP '^[0-9]+$' THEN CAST(video_share_count AS SIGNED) ELSE 0 END * 10 " +
                    "+ CASE WHEN video_play_count REGEXP '^[0-9]+$' THEN CAST(video_play_count AS SIGNED) ELSE 0 END / 10 " +
                    "+ CASE WHEN video_favorite_count REGEXP '^[0-9]+$' THEN CAST(video_favorite_count AS SIGNED) ELSE 0 END * 2 )");
            case "wb": return new PlatformMeta("wb", "微博", "weibo_note", "CAST(note_id AS CHAR)",
                    "content", "content", "note_url", "nickname", "create_time", false,
                    "liked_count", "comments_count", "shared_count", "'0'", "'0'",
                    "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                    "+ CASE WHEN comments_count REGEXP '^[0-9]+$' THEN CAST(comments_count AS SIGNED) ELSE 0 END * 5 " +
                    "+ CASE WHEN shared_count REGEXP '^[0-9]+$' THEN CAST(shared_count AS SIGNED) ELSE 0 END * 10 )");
            case "ks": return new PlatformMeta("ks", "快手", "kuaishou_video", "video_id",
                    "title", "`desc`", "video_url", "nickname", "create_time", true,
                    "liked_count", "'0'", "'0'", "viewd_count", "'0'",
                    "( CASE WHEN liked_count REGEXP '^[0-9]+$' THEN CAST(liked_count AS SIGNED) ELSE 0 END * 1 " +
                    "+ CASE WHEN viewd_count REGEXP '^[0-9]+$' THEN CAST(viewd_count AS SIGNED) ELSE 0 END / 10 )");
            default: throw new IllegalArgumentException("未知平台: " + code);
        }
    }

    /**
     * 构建一个平台的子查询。time_val 统一归一为毫秒。
     * 命名参数：:brand 必填；:kw 仅当 hasKeyword 时；:fromMs/:toMs/:fromSec/:toSec 仅当传入对应日期时（按 timeInMs 决定使用哪个）。
     */
    private String buildPlatformSubquery(PlatformMeta m, boolean hasKeyword, boolean hasFrom, boolean hasTo) {
        String timeValExpr = m.timeInMs ? m.timeCol : "(" + m.timeCol + " * 1000)";
        StringBuilder sb = new StringBuilder("(SELECT '");
        sb.append(m.platformCode).append("' AS platform, '").append(m.platformLabel).append("' AS platform_name, ");
        sb.append(m.idCast).append(" AS content_id, ");
        sb.append(m.titleExpr).append(" AS title, ");
        sb.append(m.contentExpr).append(" AS content, ");
        sb.append(m.urlCol).append(" AS url, ");
        sb.append(m.nicknameCol).append(" AS nickname, ");
        sb.append(m.likedSelect).append(" AS liked_count, ");
        sb.append(m.commentSelect).append(" AS comment_count, ");
        sb.append(m.shareSelect).append(" AS share_count, ");
        sb.append(m.viewSelect).append(" AS view_count, ");
        sb.append(m.collectedSelect).append(" AS collected_count, ");
        sb.append(timeValExpr).append(" AS time_val, ");
        sb.append(m.engagementExpr).append(" AS engagement_score ");
        sb.append("FROM ").append(m.tableName).append(" WHERE brand_name = :brand");
        if (hasFrom) {
            sb.append(" AND ").append(m.timeCol).append(" >= :").append(m.timeInMs ? "fromMs" : "fromSec");
        }
        if (hasTo) {
            sb.append(" AND ").append(m.timeCol).append(" <  :").append(m.timeInMs ? "toMs" : "toSec");
        }
        if (hasKeyword) {
            sb.append(" AND (").append(m.titleExpr).append(" LIKE :kw OR ").append(m.contentExpr).append(" LIKE :kw)");
        }
        sb.append(")");
        return sb.toString();
    }

    /** 拼接所有平台的 UNION ALL，已包含 brand/keyword/date 过滤；不含 ORDER/LIMIT */
    private String buildFilterableUnionSql(List<String> platforms, boolean hasKeyword, boolean hasFrom, boolean hasTo) {
        List<String> parts = new ArrayList<>();
        for (String p : platforms) {
            parts.add(buildPlatformSubquery(metaOf(p), hasKeyword, hasFrom, hasTo));
        }
        if (parts.isEmpty()) {
            return "SELECT 'none' AS platform, '' AS platform_name, '' AS content_id, " +
                   "'' AS title, '' AS content, '' AS url, '' AS nickname, " +
                   "'0' AS liked_count, '0' AS comment_count, '0' AS share_count, " +
                   "'0' AS view_count, '0' AS collected_count, " +
                   "0 AS time_val, 0 AS engagement_score WHERE 1=0";
        }
        return String.join(" UNION ALL ", parts);
    }

    /**
     * 带过滤的跨平台查询（不截断 content）。
     * @param brand 必填
     * @param crawlFrom 可空，"采集起始日"（按 Asia/Shanghai 0 点起算）
     * @param crawlTo   可空，"采集结束日"（含当天，按次日 0 点为半开区间右端）
     * @param platforms 可空或空集 → 全部 5 平台
     * @param keyword   可空 → 不过滤
     * @param sortBy    "engagement"（默认）或 "time"
     * @param page      ≥1
     * @param pageSize  >0
     */
    public ContentPageResponse<BuzzItemResponse> queryBuzzWithFilter(
            String brand, LocalDate crawlFrom, LocalDate crawlTo,
            List<String> platforms, String keyword, String sortBy,
            int page, int pageSize) {

        List<String> activePlatforms = (platforms == null || platforms.isEmpty()) ? ALL_PLATFORMS : platforms;
        boolean hasKw = keyword != null && !keyword.trim().isEmpty();
        boolean hasFrom = crawlFrom != null;
        boolean hasTo = crawlTo != null;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("brand", brand);
        if (hasKw) params.addValue("kw", "%" + keyword.trim() + "%");
        if (hasFrom) {
            long fromMs = crawlFrom.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            params.addValue("fromMs", fromMs);
            params.addValue("fromSec", fromMs / 1000);
        }
        if (hasTo) {
            long toMs = crawlTo.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            params.addValue("toMs", toMs);
            params.addValue("toSec", toMs / 1000);
        }

        String unionSql = buildFilterableUnionSql(activePlatforms, hasKw, hasFrom, hasTo);
        String orderClause = "time".equalsIgnoreCase(sortBy)
                ? " ORDER BY time_val DESC"
                : " ORDER BY engagement_score DESC";
        int offset = Math.max(0, (page - 1)) * Math.max(1, pageSize);
        String pagingClause = " LIMIT " + Math.max(1, pageSize) + " OFFSET " + offset;

        NamedParameterJdbcTemplate npt = new NamedParameterJdbcTemplate(jdbcTemplate);

        // 总数
        String countSql = "SELECT COUNT(*) FROM (" + unionSql + ") AS t";
        Long total = npt.queryForObject(countSql, params, Long.class);

        // 列表
        List<Map<String, Object>> rows = npt.queryForList(unionSql + orderClause + pagingClause, params);
        List<BuzzItemResponse> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(BuzzItemResponse.builder()
                    .platform(str(row, "platform"))
                    .platformName(str(row, "platform_name"))
                    .contentId(str(row, "content_id"))
                    .title(str(row, "title"))
                    .content(str(row, "content"))
                    .url(str(row, "url"))
                    .nickname(str(row, "nickname"))
                    .likedCount(str(row, "liked_count"))
                    .commentCount(str(row, "comment_count"))
                    .shareCount(str(row, "share_count"))
                    .viewCount(str(row, "view_count"))
                    .collectedCount(str(row, "collected_count"))
                    .time(toLong(row.get("time_val")))
                    .engagementScore(toLong(row.get("engagement_score")))
                    .build());
        }

        return ContentPageResponse.<BuzzItemResponse>builder()
                .list(list)
                .total(total != null ? total : 0L)
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    // ===== 评论查询：按 contentId 取某条内容的评论 =====

    /** 按平台 + contentId 查评论，按点赞数降序，限 limit 条。 */
    public List<CommentItemResponse> queryComments(String platform, String contentId, int limit) {
        if (contentId == null || contentId.isEmpty()) return new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql;
        Object idParam = contentId;

        switch (platform) {
            case "xhs":
                // note_id 是 VARCHAR
                sql = "SELECT comment_id AS comment_id, content, nickname, " +
                      "COALESCE(like_count, '0') AS like_count, " +
                      "CAST(COALESCE(sub_comment_count, 0) AS CHAR) AS sub_comment_count, " +
                      "create_time AS time_val " +
                      "FROM xhs_note_comment WHERE note_id = ? " +
                      "ORDER BY (CASE WHEN like_count REGEXP '^[0-9]+$' THEN CAST(like_count AS SIGNED) ELSE 0 END) DESC " +
                      "LIMIT " + safeLimit;
                break;
            case "dy":
                // aweme_id 是 BIGINT
                sql = "SELECT CAST(comment_id AS CHAR) AS comment_id, content, nickname, " +
                      "COALESCE(like_count, '0') AS like_count, " +
                      "COALESCE(sub_comment_count, '0') AS sub_comment_count, " +
                      "create_time AS time_val " +
                      "FROM douyin_aweme_comment WHERE aweme_id = ? " +
                      "ORDER BY (CASE WHEN like_count REGEXP '^[0-9]+$' THEN CAST(like_count AS SIGNED) ELSE 0 END) DESC " +
                      "LIMIT " + safeLimit;
                idParam = parseLongOrZero(contentId);
                break;
            case "bili":
                sql = "SELECT CAST(comment_id AS CHAR) AS comment_id, content, nickname, " +
                      "COALESCE(like_count, '0') AS like_count, " +
                      "COALESCE(sub_comment_count, '0') AS sub_comment_count, " +
                      "create_time AS time_val " +
                      "FROM bilibili_video_comment WHERE video_id = ? " +
                      "ORDER BY (CASE WHEN like_count REGEXP '^[0-9]+$' THEN CAST(like_count AS SIGNED) ELSE 0 END) DESC " +
                      "LIMIT " + safeLimit;
                idParam = parseLongOrZero(contentId);
                break;
            case "wb":
                // wb 用 comment_like_count 而非 like_count
                sql = "SELECT CAST(comment_id AS CHAR) AS comment_id, content, nickname, " +
                      "COALESCE(comment_like_count, '0') AS like_count, " +
                      "COALESCE(sub_comment_count, '0') AS sub_comment_count, " +
                      "create_time AS time_val " +
                      "FROM weibo_note_comment WHERE note_id = ? " +
                      "ORDER BY (CASE WHEN comment_like_count REGEXP '^[0-9]+$' THEN CAST(comment_like_count AS SIGNED) ELSE 0 END) DESC " +
                      "LIMIT " + safeLimit;
                idParam = parseLongOrZero(contentId);
                break;
            case "ks":
                // 快手没有 like_count 字段
                sql = "SELECT CAST(comment_id AS CHAR) AS comment_id, content, nickname, " +
                      "'0' AS like_count, " +
                      "COALESCE(sub_comment_count, '0') AS sub_comment_count, " +
                      "create_time AS time_val " +
                      "FROM kuaishou_video_comment WHERE video_id = ? " +
                      "ORDER BY create_time DESC " +
                      "LIMIT " + safeLimit;
                break;
            default:
                return new ArrayList<>();
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, idParam);
        List<CommentItemResponse> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(CommentItemResponse.builder()
                    .commentId(str(row, "comment_id"))
                    .content(str(row, "content"))
                    .nickname(str(row, "nickname"))
                    .likeCount(str(row, "like_count"))
                    .subCommentCount(str(row, "sub_comment_count"))
                    .time(toLong(row.get("time_val")))
                    .build());
        }
        return out;
    }

    private long parseLongOrZero(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }
}
