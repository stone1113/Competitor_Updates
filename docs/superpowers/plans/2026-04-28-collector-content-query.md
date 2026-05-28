# 采集内容查询功能 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理后台增加"采集内容查看"页面，通过两个 Tab 分别查询新闻（`web_search_news`）和社交平台内容（5 张平台表 UNION ALL），支持品牌/关键词/日期范围/平台/排序等筛选与分页。

**Architecture:** 后端新增 `ContentQueryController` 提供两个 GET 端点；新闻走 MyBatis-Plus `IPage`+`QueryWrapper` 分页；社交在 `PlatformQueryService` 新增 `queryBuzzWithFilter`/`countBuzzWithFilter`，用 `NamedParameterJdbcTemplate` 参数化查询，时间统一归一为毫秒、内容不截断。前端新增独立页面 `ContentViewView.vue`，分两个 Tab，点击表格行用 `el-drawer` 显示全文。

**Tech Stack:** Java 11 / Spring Boot 2.7.18 / MyBatis-Plus / Spring JDBC NamedParameterJdbcTemplate / Vue 3 / Element Plus / axios

**仓库特性提示：** 项目当前**不是 git 仓库**，所以本计划用"构建/curl/浏览器验证"取代各步末尾的 `git commit`。如需版本管理，最后统一 `git init && git add . && git commit`。

**项目无测试基础设施**（无 `src/test/java`，无 Vitest），因此每个任务采用"实现 → 编译 → curl/浏览器实测"的验证节奏，不强制 TDD。

---

## File Structure

| 路径 | 操作 | 责任 |
|---|---|---|
| `nev-model/src/main/java/com/nevinsight/model/dto/response/ContentPageResponse.java` | 新增 | 通用分页 DTO `{list, total, page, pageSize}` |
| `nev-collector/src/main/java/com/nevinsight/collector/service/PlatformQueryService.java` | 修改 | 追加 `queryBuzzWithFilter` + `countBuzzWithFilter`（保留旧方法不动） |
| `nev-admin/src/main/java/com/nevinsight/admin/controller/v1/ContentQueryController.java` | 新增 | `GET /api/v1/content/news`、`GET /api/v1/content/social` |
| `nev-admin-ui/src/api/content.js` | 新增 | `queryNews`、`querySocial` 函数 |
| `nev-admin-ui/src/views/collector/ContentViewView.vue` | 新增 | 主页面（顶部公共筛选 + 两 Tab + 抽屉 + 分页） |
| `nev-admin-ui/src/router/index.js` | 修改 | 增加 `collector/content-view` 路由 |
| `nev-admin-ui/src/components/layout/AppSidebar.vue` | 修改 | "采集管理"子菜单下增加菜单项 |

---

## Task 1: 通用分页 DTO `ContentPageResponse`

**Files:**
- Create: `nev-model/src/main/java/com/nevinsight/model/dto/response/ContentPageResponse.java`

- [ ] **Step 1: 创建 DTO 文件**

```java
package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentPageResponse<T> {
    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl nev-model -am -q`
Expected: BUILD SUCCESS（无编译错误）

---

## Task 2: 扩展 `PlatformQueryService` 支持过滤查询

**Files:**
- Modify: `nev-collector/src/main/java/com/nevinsight/collector/service/PlatformQueryService.java`

实现要点：
- **保留**原 `queryBuzzUnion` 方法不动（被 `nev-report` 使用）
- 新增方法用 `NamedParameterJdbcTemplate` 做参数化绑定
- 时间归一：xhs/ks 的 `time` 已是毫秒；dy/bili/wb 的 `create_time` 是秒，乘 1000 转毫秒
- 关键词在 title 和 content 字段做 OR LIKE 匹配
- 日期范围：crawlFrom 取 0 点 ms，crawlTo 取**次日** 0 点 ms（半开区间 `[from, to)`）
- 内容**不截断**

- [ ] **Step 1: 在文件顶部 import 区追加导入**

打开 `PlatformQueryService.java`，把现有 import 区替换为：

```java
import com.nevinsight.model.dto.response.BuzzItemResponse;
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
```

- [ ] **Step 2: 在类尾追加平台元数据辅助方法**

在 `truncate(...)` 方法之后（即类闭合 `}` 之前）追加：

```java
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
```

- [ ] **Step 3: 追加 SQL 构建方法**

紧接着 `metaOf` 方法之后追加：

```java
    /**
     * 构建一个平台的子查询。time_val 统一归一为毫秒。
     * 命名参数：:brand 必填；:kw 仅当 hasKeyword 时；:fromMs/:toMs 仅当传入对应日期时（内部按 timeInMs 决定除以 1000）。
     */
    private String buildPlatformSubquery(PlatformMeta m, boolean hasKeyword, boolean hasFrom, boolean hasTo) {
        // time_val 归一: ms 平台直接用，sec 平台 *1000
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
            // title/content 有可能是同一字段（微博），用 OR 也无副作用
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
```

- [ ] **Step 4: 追加查询与计数公共方法**

紧接 `buildFilterableUnionSql` 之后追加：

```java
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
```

- [ ] **Step 5: 验证编译**

Run: `mvn compile -pl nev-collector -am -q`
Expected: BUILD SUCCESS

---

## Task 3: 新建 `ContentQueryController`

**Files:**
- Create: `nev-admin/src/main/java/com/nevinsight/admin/controller/v1/ContentQueryController.java`

- [ ] **Step 1: 创建控制器**

```java
package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nevinsight.collector.service.PlatformQueryService;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.dto.response.BuzzItemResponse;
import com.nevinsight.model.dto.response.ContentPageResponse;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentQueryController {

    private final WebSearchNewsMapper webSearchNewsMapper;
    private final PlatformQueryService platformQueryService;

    /**
     * 查询采集到的搜索引擎新闻
     */
    @GetMapping("/news")
    public ApiResponse<ContentPageResponse<WebSearchNews>> queryNews(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlTo,
            @RequestParam(required = false) String publishedFrom,
            @RequestParam(required = false) String publishedTo,
            @RequestParam(required = false) String sourceTool,
            @RequestParam(defaultValue = "crawlDate") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        QueryWrapper<WebSearchNews> qw = new QueryWrapper<>();
        if (brand != null && !brand.isEmpty()) qw.eq("brand_name", brand);
        if (sourceTool != null && !sourceTool.isEmpty()) qw.eq("source_tool", sourceTool);
        if (crawlFrom != null) qw.ge("crawl_date", crawlFrom);
        if (crawlTo != null) qw.le("crawl_date", crawlTo);
        if (publishedFrom != null && !publishedFrom.isEmpty()) qw.ge("published_date", publishedFrom);
        if (publishedTo != null && !publishedTo.isEmpty()) qw.le("published_date", publishedTo);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("title", kw).or().like("content", kw).or().like("search_query", kw));
        }
        if ("relevance".equalsIgnoreCase(sortBy)) {
            qw.orderByDesc("relevance_score");
        } else {
            qw.orderByDesc("crawl_date").orderByDesc("id");
        }

        Page<WebSearchNews> p = new Page<>(page, pageSize);
        Page<WebSearchNews> result = webSearchNewsMapper.selectPage(p, qw);

        ContentPageResponse<WebSearchNews> resp = ContentPageResponse.<WebSearchNews>builder()
                .list(result.getRecords())
                .total(result.getTotal())
                .page(page)
                .pageSize(pageSize)
                .build();
        return ApiResponse.success(resp);
    }

    /**
     * 查询采集到的社交平台内容（5 张表 UNION ALL）
     */
    @GetMapping("/social")
    public ApiResponse<ContentPageResponse<BuzzItemResponse>> querySocial(
            @RequestParam String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlTo,
            @RequestParam(required = false) String platforms,
            @RequestParam(defaultValue = "engagement") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        List<String> platformList = (platforms == null || platforms.isEmpty())
                ? Collections.emptyList()
                : Arrays.asList(platforms.split(","));

        ContentPageResponse<BuzzItemResponse> resp = platformQueryService.queryBuzzWithFilter(
                brand, crawlFrom, crawlTo, platformList, keyword, sortBy, page, pageSize);
        return ApiResponse.success(resp);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl nev-admin -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 打包并启动后端，做 curl 验证**

Run: `mvn package -DskipTests -pl nev-admin -am -q`
Expected: BUILD SUCCESS

启动后端（如果已通过 Docker 在跑，则 `docker compose up -d --build app` 重建；否则用 IDE 启动 NevAdminApplication）。等待端口 8090 可用后测试：

```bash
# 新闻 - 全部
curl -s 'http://localhost:8090/api/v1/content/news?page=1&pageSize=5' | head -c 500

# 新闻 - 带筛选
curl -s 'http://localhost:8090/api/v1/content/news?brand=猛士&keyword=电池&page=1&pageSize=5' | head -c 500

# 社交 - 必填 brand
curl -s 'http://localhost:8090/api/v1/content/social?brand=猛士&page=1&pageSize=5' | head -c 500

# 社交 - 限定平台 + 关键词 + 日期
curl -s 'http://localhost:8090/api/v1/content/social?brand=猛士&platforms=xhs,dy&keyword=续航&crawlFrom=2026-04-20&crawlTo=2026-04-28&page=1&pageSize=3' | head -c 500
```

Expected: 每个请求都返回 `{"code":200,"message":"...","data":{"list":[...],"total":N,"page":1,"pageSize":...}}` 结构，无 500 错误。如某品牌没有数据则 `list:[]` 且 `total:0` 也算 PASS。

---

## Task 4: 前端 API 文件 `content.js`

**Files:**
- Create: `nev-admin-ui/src/api/content.js`

- [ ] **Step 1: 创建 API 模块**

```javascript
import api from '../composables/useApi'

/**
 * 查询采集新闻
 * @param {Object} params { brand, keyword, crawlFrom, crawlTo, publishedFrom, publishedTo, sourceTool, sortBy, page, pageSize }
 */
export const queryNews = (params) =>
  api.get('/api/v1/content/news', { params })

/**
 * 查询社交平台内容
 * @param {Object} params { brand, keyword, crawlFrom, crawlTo, platforms, sortBy, page, pageSize }
 *   platforms 为逗号分隔字符串，如 "xhs,dy"
 */
export const querySocial = (params) =>
  api.get('/api/v1/content/social', { params })
```

---

## Task 5: 前端页面 `ContentViewView.vue`

**Files:**
- Create: `nev-admin-ui/src/views/collector/ContentViewView.vue`

- [ ] **Step 1: 创建页面文件**

```vue
<template>
  <div class="page-container">
    <!-- 顶部公共筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="commonFilter" inline>
        <el-form-item label="品牌">
          <el-select v-model="commonFilter.brand" placeholder="全部品牌" clearable style="width:140px">
            <el-option label="猛士" value="猛士" />
            <el-option label="坦克" value="坦克" />
            <el-option label="方程豹" value="方程豹" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="commonFilter.keyword" placeholder="标题/内容模糊匹配" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="采集日期">
          <el-date-picker
            v-model="commonFilter.crawlRange"
            type="daterange"
            range-separator="至"
            start-placeholder="起"
            end-placeholder="止"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width:240px"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-select v-model="commonFilter.sortBy" style="width:140px">
            <template v-if="activeTab === 'news'">
              <el-option label="采集时间" value="crawlDate" />
              <el-option label="相关度" value="relevance" />
            </template>
            <template v-else>
              <el-option label="互动量" value="engagement" />
              <el-option label="发布时间" value="time" />
            </template>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange" style="margin-top:12px">
      <el-tab-pane label="新闻" name="news">
        <!-- 新闻独有筛选 -->
        <el-form :model="newsFilter" inline style="margin-bottom:12px">
          <el-form-item label="发布日期">
            <el-date-picker
              v-model="newsFilter.publishedRange"
              type="daterange"
              range-separator="至"
              start-placeholder="起"
              end-placeholder="止"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width:240px"
            />
          </el-form-item>
          <el-form-item label="数据源">
            <el-select v-model="newsFilter.sourceTool" placeholder="全部" clearable style="width:140px">
              <el-option label="BochaAI" value="bocha" />
              <el-option label="Tavily" value="tavily" />
              <el-option label="Anspire" value="anspire" />
            </el-select>
          </el-form-item>
        </el-form>

        <el-table :data="newsList" stripe @row-click="openDrawer" style="cursor:pointer">
          <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
          <el-table-column prop="brandName" label="品牌" width="80" />
          <el-table-column prop="searchQuery" label="搜索词" width="120" show-overflow-tooltip />
          <el-table-column prop="publishedDate" label="发布日期" width="110" />
          <el-table-column prop="crawlDate" label="采集日期" width="110" />
          <el-table-column prop="sourceTool" label="数据源" width="90" />
          <el-table-column prop="relevanceScore" label="相关度" width="80">
            <template #default="{ row }">
              {{ row.relevanceScore != null ? row.relevanceScore.toFixed(2) : '-' }}
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="社交平台" name="social">
        <!-- 社交独有筛选 -->
        <el-form :model="socialFilter" inline style="margin-bottom:12px">
          <el-form-item label="平台">
            <el-select v-model="socialFilter.platforms" multiple collapse-tags placeholder="全部平台" style="width:280px">
              <el-option label="小红书" value="xhs" />
              <el-option label="抖音" value="dy" />
              <el-option label="B站" value="bili" />
              <el-option label="微博" value="wb" />
              <el-option label="快手" value="ks" />
            </el-select>
          </el-form-item>
        </el-form>

        <el-table :data="socialList" stripe @row-click="openDrawer" style="cursor:pointer">
          <el-table-column prop="platformName" label="平台" width="80">
            <template #default="{ row }">
              <el-tag size="small">{{ row.platformName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
          <el-table-column prop="nickname" label="作者" width="120" show-overflow-tooltip />
          <el-table-column prop="likedCount" label="点赞" width="80" />
          <el-table-column prop="commentCount" label="评论" width="80" />
          <el-table-column prop="shareCount" label="分享" width="80" />
          <el-table-column label="发布时间" width="160">
            <template #default="{ row }">{{ formatTime(row.time) }}</template>
          </el-table-column>
          <el-table-column prop="engagementScore" label="互动量" width="100" sortable />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 分页 -->
    <div style="margin-top:12px;display:flex;justify-content:flex-end">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="50%" direction="rtl">
      <div v-if="currentRow" class="drawer-body">
        <h3 style="margin-top:0">{{ currentRow.title || '无标题' }}</h3>
        <p v-if="currentRow.url">
          <a :href="currentRow.url" target="_blank" rel="noopener noreferrer" style="color:#409eff">
            {{ currentRow.url }}
          </a>
        </p>
        <el-divider />
        <div class="meta">
          <template v-if="activeTab === 'news'">
            <div>品牌：{{ currentRow.brandName }}</div>
            <div>搜索词：{{ currentRow.searchQuery }}</div>
            <div>发布日期：{{ currentRow.publishedDate || '-' }}</div>
            <div>采集日期：{{ currentRow.crawlDate }}</div>
            <div>数据源：{{ currentRow.sourceTool }}</div>
            <div v-if="currentRow.relevanceScore != null">相关度：{{ currentRow.relevanceScore.toFixed(2) }}</div>
          </template>
          <template v-else>
            <div>平台：{{ currentRow.platformName }}</div>
            <div>作者：{{ currentRow.nickname }}</div>
            <div>发布时间：{{ formatTime(currentRow.time) }}</div>
            <div>点赞 {{ currentRow.likedCount }} · 评论 {{ currentRow.commentCount }} · 分享 {{ currentRow.shareCount }} · 收藏 {{ currentRow.collectedCount }} · 播放 {{ currentRow.viewCount }}</div>
            <div>互动量：{{ currentRow.engagementScore }}</div>
          </template>
        </div>
        <el-divider />
        <div class="content-text">{{ currentRow.content || '（无正文）' }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { queryNews, querySocial } from '../../api/content'

const activeTab = ref('news')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const commonFilter = ref({
  brand: '',
  keyword: '',
  crawlRange: [],
  sortBy: 'crawlDate'
})
const newsFilter = ref({ publishedRange: [], sourceTool: '' })
const socialFilter = ref({ platforms: [] })

const newsList = ref([])
const socialList = ref([])

const drawerVisible = ref(false)
const currentRow = ref(null)
const drawerTitle = ref('详情')

function formatTime(ms) {
  if (!ms) return '-'
  const d = new Date(Number(ms))
  if (isNaN(d.getTime())) return '-'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function buildCommonParams() {
  const p = {
    page: page.value,
    pageSize: pageSize.value,
    sortBy: commonFilter.value.sortBy
  }
  if (commonFilter.value.brand) p.brand = commonFilter.value.brand
  if (commonFilter.value.keyword) p.keyword = commonFilter.value.keyword
  const r = commonFilter.value.crawlRange
  if (r && r.length === 2) { p.crawlFrom = r[0]; p.crawlTo = r[1] }
  return p
}

async function loadData() {
  try {
    if (activeTab.value === 'news') {
      const params = buildCommonParams()
      const pr = newsFilter.value.publishedRange
      if (pr && pr.length === 2) { params.publishedFrom = pr[0]; params.publishedTo = pr[1] }
      if (newsFilter.value.sourceTool) params.sourceTool = newsFilter.value.sourceTool
      const res = await queryNews(params)
      newsList.value = res.list || []
      total.value = res.total || 0
    } else {
      if (!commonFilter.value.brand) {
        ElMessage.warning('社交平台查询需要选择品牌')
        socialList.value = []
        total.value = 0
        return
      }
      const params = buildCommonParams()
      if (socialFilter.value.platforms && socialFilter.value.platforms.length > 0) {
        params.platforms = socialFilter.value.platforms.join(',')
      }
      const res = await querySocial(params)
      socialList.value = res.list || []
      total.value = res.total || 0
    }
  } catch (e) {
    ElMessage.error('查询失败：' + (e.message || e))
  }
}

function handleQuery() {
  page.value = 1
  loadData()
}

function handleReset() {
  commonFilter.value = { brand: '', keyword: '', crawlRange: [], sortBy: activeTab.value === 'news' ? 'crawlDate' : 'engagement' }
  newsFilter.value = { publishedRange: [], sourceTool: '' }
  socialFilter.value = { platforms: [] }
  page.value = 1
  loadData()
}

function handleTabChange() {
  page.value = 1
  // 排序默认值随 Tab 切换
  commonFilter.value.sortBy = activeTab.value === 'news' ? 'crawlDate' : 'engagement'
  loadData()
}

function openDrawer(row) {
  currentRow.value = row
  drawerTitle.value = activeTab.value === 'news' ? '新闻详情' : '内容详情'
  drawerVisible.value = true
}

onMounted(loadData)
</script>

<style scoped>
.filter-card {
  border-radius: 8px;
}
.drawer-body {
  padding: 0 20px 20px;
}
.drawer-body .meta {
  color: #606266;
  font-size: 13px;
  line-height: 1.8;
}
.drawer-body .content-text {
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
  font-size: 14px;
  line-height: 1.7;
  max-height: calc(100vh - 360px);
  overflow-y: auto;
}
</style>
```

---

## Task 6: 配置路由与侧边栏菜单

**Files:**
- Modify: `nev-admin-ui/src/router/index.js`
- Modify: `nev-admin-ui/src/components/layout/AppSidebar.vue`

- [ ] **Step 1: 添加路由**

在 `nev-admin-ui/src/router/index.js` 的 `children` 数组中，在 `cleaning-rule` 路由之后插入：

```javascript
      {
        path: 'collector/content-view',
        name: 'ContentView',
        component: () => import('../views/collector/ContentViewView.vue'),
        meta: { title: '采集内容查看', icon: 'View' }
      },
```

修改后的相邻片段示例：

```javascript
      {
        path: 'collector/cleaning-rule',
        name: 'CleaningRule',
        component: () => import('../views/collector/CleaningRuleView.vue'),
        meta: { title: '清洗规则', icon: 'Filter' }
      },
      {
        path: 'collector/content-view',
        name: 'ContentView',
        component: () => import('../views/collector/ContentViewView.vue'),
        meta: { title: '采集内容查看', icon: 'View' }
      },
```

- [ ] **Step 2: 添加侧边栏菜单项**

在 `nev-admin-ui/src/components/layout/AppSidebar.vue` 中找到 `<el-sub-menu index="collector">` 块，在 `清洗规则` 那一行之后插入：

```vue
        <el-menu-item index="/collector/content-view">采集内容查看</el-menu-item>
```

修改后的相邻片段示例：

```vue
      <el-sub-menu index="collector">
        <template #title>
          <el-icon><Connection /></el-icon>
          <span>采集管理</span>
        </template>
        <el-menu-item index="/collector/data-source">数据源配置</el-menu-item>
        <el-menu-item index="/collector/cleaning-rule">清洗规则</el-menu-item>
        <el-menu-item index="/collector/content-view">采集内容查看</el-menu-item>
      </el-sub-menu>
```

- [ ] **Step 3: 前端启动并验证**

Run:
```bash
cd /Users/anliwu/claude-pro/m-monitor/nev-admin-ui && npm run dev
```
Expected: Vite dev server 在 3000 端口启动，无编译错误。

---

## Task 7: 浏览器端到端验证

**Files:**（无文件改动，仅人工/浏览器验证）

- [ ] **Step 1: 打开页面**

浏览器打开 `http://localhost:3000/collector/content-view`
Expected:
- 左侧菜单"采集管理"展开后能看到"采集内容查看"
- 顶部出现公共筛选栏（品牌、关键词、采集日期、排序）
- Tabs 默认在"新闻"，下方有发布日期和数据源筛选
- 表格加载出新闻数据（如果有），底部分页可见

- [ ] **Step 2: 验证筛选**

依次操作并点击"查询"：
1. 选品牌"猛士" → 列表只显示该品牌
2. 输入关键词"电池" → 列表只显示标题/内容/搜索词命中的
3. 选采集日期范围 → 列表按范围过滤
4. 切换排序"相关度" → 列表按 relevance_score 倒序

Expected: 每次查询后 total 与表格行数同步更新；分页大小切换后回到第 1 页能正常加载

- [ ] **Step 3: 验证抽屉**

点击表格任意一行
Expected: 右侧 50% 宽度抽屉滑出，显示标题、可点击的 URL、元数据、完整正文（可滚动）

- [ ] **Step 4: 切换到社交 Tab**

点击"社交平台" Tab
Expected:
- 公共筛选保留（品牌/关键词/采集日期/排序）
- 排序下拉的选项变成"互动量 / 发布时间"
- 出现独有的"平台"多选
- 若品牌未选，提示"社交平台查询需要选择品牌"且表格为空
- 选品牌后列表加载，列：平台 / 标题 / 作者 / 点赞 / 评论 / 分享 / 发布时间 / 互动量
- 选择平台多选（如只选"小红书 + 抖音"）后查询，列表 platform 列只出现这两个

- [ ] **Step 5: 验证社交 Tab 抽屉**

点击社交 Tab 表格任意一行
Expected: 抽屉显示完整的平台名、作者、互动数据、发布时间、完整正文

- [ ] **Step 6: 重置按钮**

点击"重置"按钮
Expected: 所有筛选清空，分页回第 1 页，列表重新加载

---

## 完成后

整体功能完成，无遗留编译/运行错误。

如需进入 git 管理，执行：

```bash
cd /Users/anliwu/claude-pro/m-monitor
git init
git add .
git commit -m "feat: 增加采集内容查看功能（新闻 + 社交平台双 Tab）"
```

---

## Self-Review

**Spec coverage:**
- §1 后端 API → Task 1（DTO）+ Task 2（PlatformQueryService 扩展）+ Task 3（Controller）✓
- §2 前端 → Task 4（API）+ Task 5（页面）+ Task 6（路由+菜单）✓
- §3 数据契约（content 不截断、time 归一为毫秒、日期字符串）→ Task 2 SQL 中 `time_val` 归一、不截断已在 step 4 体现 ✓
- §4 落地清单 7 个文件 → 7 个 Task 全覆盖 ✓
- 测试方案 → Task 3 step 3（curl）+ Task 7（浏览器）✓
- 风险点（SQL 注入参数化、关键词全表扫接受、count 代价接受、微博 title=content 已知行为）→ Task 2 用 NamedParameterJdbcTemplate；微博 metaOf 中 titleExpr/contentExpr 都用 `content`，OR LIKE 仍能命中 ✓

**Placeholder scan:** 无 TBD/TODO；所有代码块完整可粘贴；命令均给出预期输出。✓

**Type/命名一致性:**
- Backend：`ContentPageResponse<T>` 字段 `list/total/page/pageSize` 在 Task 1 定义、Task 2/3 使用 ✓
- API 路径：`/api/v1/content/news`、`/api/v1/content/social` 在 Controller 与前端 `content.js` 一致 ✓
- 参数名：`brand/keyword/crawlFrom/crawlTo/publishedFrom/publishedTo/sourceTool/platforms/sortBy/page/pageSize` 在前后端一致 ✓
- 排序值：news 用 `crawlDate`/`relevance`；social 用 `engagement`/`time`，前后端一致 ✓
- 平台 code：`xhs/dy/bili/wb/ks` 后端 `metaOf` 与前端 `socialFilter.platforms` 选项一致 ✓
