# 采集内容查询功能 设计文档

**日期**：2026-04-28
**作者**：Claude + 用户
**状态**：已完成设计，待实现

## 背景

当前系统已经在采集两类内容：
1. **新闻**：通过 BochaAI 搜索引擎采集，落库到 `web_search_news` 表
2. **社交平台内容**：通过 MediaCrawler 采集，落库到 `xhs_note`、`douyin_aweme`、`bilibili_video`、`weibo_note`、`kuaishou_video` 5 张平台表

但管理后台没有页面可以让用户查看到底采集到了哪些内容。`CollectorController` 只暴露了触发采集的 `POST /collect-news`，没有查询接口。

## 目标

在管理后台增加"采集内容查看"页面，让用户可以筛选、浏览所有采集到的新闻和社交平台内容。

## 非目标（YAGNI）

- 不提供删除（单条 / 批量）
- 不提供导出 CSV
- 不在该页面提供"手动触发采集"按钮（已有独立入口）

## 总体方案

- **UI 形态**：分两个 Tab（新闻 / 社交平台），表格 + 抽屉（点行展开看全文），底部分页
- **筛选**：品牌、关键词、采集日期范围、排序为公共筛选项；新闻 Tab 额外有发布日期范围、数据源；社交 Tab 额外有平台多选
- **后端**：新增 `ContentQueryController` 提供两个 GET 端点；新闻走 MyBatis-Plus 分页；社交走 `PlatformQueryService` 新增的 `queryBuzzWithFilter`（保留旧方法不动）

---

## §1 后端 API

新建 **`ContentQueryController`**（基础路径 `/api/v1/content`），两个查询端点：

| 端点 | 用途 | 主要参数 |
|---|---|---|
| `GET /news` | 查询采集新闻 | `brand`、`keyword`、`crawlFrom`、`crawlTo`、`publishedFrom`、`publishedTo`、`sourceTool`、`sortBy`(relevance/crawlDate)、`page`、`pageSize` |
| `GET /social` | 查询社交平台内容 | `brand`、`keyword`、`crawlFrom`、`crawlTo`、`platforms`(逗号分隔，可全部)、`sortBy`(engagement/time)、`page`、`pageSize` |

返回统一分页结构：`ApiResponse<ContentPageResponse<T>>`，其中 `ContentPageResponse` = `{list, total, page, pageSize}`。

**实现要点**：

- **新闻** → `WebSearchNewsMapper` 用 MyBatis-Plus 的 `IPage` + `QueryWrapper` 完成。关键词用 `like` 在 `title` / `content` / `search_query` 上做 OR 模糊匹配。
- **社交** → 在 `PlatformQueryService` 新增两个方法：
  - `queryBuzzWithFilter(brand, dateFrom, dateTo, platforms, keyword, sortBy, limit, offset)` → 返回完整 `BuzzItemResponse` 列表（**不截断 content**）
  - `countBuzzWithFilter(brand, dateFrom, dateTo, platforms, keyword)` → 返回 UNION ALL 后的总数
- **保留** `queryBuzzUnion` 方法不修改，避免影响 `nev-report` 的日报生成路径

## §2 前端

**路由**：在"数据采集"组下新增菜单项 `collector/content-view` → "**采集内容查看**"，图标 `View`。

**页面**：`nev-admin-ui/src/views/collector/ContentViewView.vue`

**结构**：

```
顶部筛选栏（el-form inline，公共筛选项）
  ├─ 品牌下拉
  ├─ 关键词输入框
  ├─ 采集日期范围（el-date-picker type=daterange）
  ├─ 排序下拉
  └─ 查询 / 重置 按钮

el-tabs
  ├─ Tab1 「新闻」
  │   ├─ 额外筛选：发布日期范围 + 数据源下拉（bocha 等）
  │   └─ el-table 列：标题 / 品牌 / 关键词 / 发布日期 / 采集日期 / 数据源 / 相关度
  │       点击行 → 右侧 el-drawer，显示标题、URL（可点）、全文
  │
  └─ Tab2 「社交平台」
      ├─ 额外筛选：平台多选（el-select multiple，5 个平台）
      └─ el-table 列：平台 / 标题 / 品牌 / 作者 / 点赞 / 评论 / 分享 / 时间 / 互动量
          点击行 → 右侧 el-drawer，显示标题、URL（可点）、作者、互动数据、全文

底部 el-pagination（每页 20/50/100，跟随当前 Tab）
```

**API 文件**：`nev-admin-ui/src/api/content.js` 导出 `queryNews(params)`、`querySocial(params)`。

**Tab 切换行为**：
- 切换 Tab 时回到第 1 页
- 顶部公共筛选项（品牌、关键词、采集日期、排序）保留
- 各 Tab 独有的筛选项各自维护（独立的 ref 对象）

## §3 数据契约

### 新闻查询响应（`GET /api/v1/content/news`）

```json
{
  "code": 200,
  "data": {
    "total": 156,
    "page": 1,
    "pageSize": 20,
    "list": [
      {
        "id": 123,
        "brandName": "小米",
        "searchQuery": "小米SU7",
        "title": "...",
        "url": "https://...",
        "content": "...",
        "publishedDate": "2026-04-27",
        "crawlDate": "2026-04-28",
        "sourceTool": "bocha",
        "relevanceScore": 0.92
      }
    ]
  }
}
```

### 社交查询响应（`GET /api/v1/content/social`）

```json
{
  "code": 200,
  "data": {
    "total": 380,
    "page": 1,
    "pageSize": 20,
    "list": [
      {
        "platform": "xhs",
        "platformName": "小红书",
        "contentId": "...",
        "title": "...",
        "content": "...",
        "url": "...",
        "nickname": "...",
        "likedCount": "1024",
        "commentCount": "88",
        "shareCount": "12",
        "viewCount": "0",
        "collectedCount": "55",
        "time": 1745800000000,
        "engagementScore": 1996
      }
    ]
  }
}
```

### 关键约定

1. **content 不截断**：`queryBuzzWithFilter` 返回完整内容（不同于 `queryBuzzUnion` 的 100/200 字截断），抽屉需要看全文
2. **time 字段统一为毫秒**：5 张表底层时间单位不同（xhs/ks 毫秒，dy/bili/wb 秒），SQL 层用 `* 1000` 归一为毫秒
3. **日期字段用字符串 `YYYY-MM-DD`** 传输，避免时区问题
4. **社交 Tab 的"采集日期"** 实际映射到 `time` / `create_time`（社交平台表无 `crawl_date` 字段）

## §4 落地清单

| 类型 | 路径 | 说明 |
|---|---|---|
| 新增 | `nev-model/src/main/java/com/nevinsight/model/dto/response/ContentPageResponse.java` | 通用分页 DTO `{list, total, page, pageSize}` |
| 修改 | `nev-collector/src/main/java/com/nevinsight/collector/service/PlatformQueryService.java` | 新增 `queryBuzzWithFilter` + `countBuzzWithFilter`，不截断；时间归一为毫秒；用 PreparedStatement 参数化 |
| 新增 | `nev-admin/src/main/java/com/nevinsight/admin/controller/v1/ContentQueryController.java` | 两个 GET 端点 |
| 新增 | `nev-admin-ui/src/api/content.js` | `queryNews`、`querySocial` |
| 新增 | `nev-admin-ui/src/views/collector/ContentViewView.vue` | 主页面 |
| 修改 | `nev-admin-ui/src/router/index.js` | 增加 `collector/content-view` 路由 |

## 测试方案

1. **后端**：启动 `nev-admin`，用 `curl` 验证两个端点：分页、品牌筛选、关键词模糊匹配、日期范围、排序
2. **前端**：`npm run dev`，浏览器逐项验证：
   - 品牌筛选生效
   - 关键词搜索（标题/内容命中）
   - 采集日期范围
   - 发布日期范围（新闻）
   - 平台多选（社交）
   - Tab 切换时公共筛选项保留、独有筛选项独立
   - 点击行展开抽屉，全文可见、URL 可点击
   - 分页前后翻页

## 风险与注意

1. **SQL 注入**：现有 `queryBuzzUnion` 直接拼 brand 字符串。新方法 `queryBuzzWithFilter` 用 `?` 参数化绑定 brand 和 keyword
2. **关键词模糊匹配性能**：5 张表 `LIKE '%xxx%'` 全表扫描可能慢。MVP 阶段接受，未来如需优化可加 FULLTEXT 索引
3. **count 查询代价**：UNION ALL 5 张表的 `SELECT COUNT(*)` 在大数据量下可能慢。MVP 接受
4. **微博 `title=content`**：现有代码把 weibo 的 content 字段当 title 返回，前端会出现"标题=正文"，是已有行为，不在本次修改范围
