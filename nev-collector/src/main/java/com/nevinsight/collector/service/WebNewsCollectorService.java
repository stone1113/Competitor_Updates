package com.nevinsight.collector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nevinsight.collector.client.BochaApiClient;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.BrandKeywordConfig;
import com.nevinsight.model.entity.core.NewsUrlBlacklist;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.BrandKeywordConfigMapper;
import com.nevinsight.model.mapper.core.NewsUrlBlacklistMapper;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 新闻采集 v2：完全由 brand_keyword_config 表驱动。
 *
 *  每条 enabled keyword → 一次 BochaAI 查询 → 入 web_search_news（去重 by url_hash）
 *  keyword.category (self/competitor/industry) → web_search_news.category
 *
 * 不再读 data_source_config（v1 遗留）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebNewsCollectorService {

    public static final String CATEGORY_SELF = "self";
    public static final String CATEGORY_COMPETITOR = "competitor";
    public static final String CATEGORY_INDUSTRY = "industry";

    private static final List<MarketHotQuery> MARKET_HOT_QUERIES = List.of(
            new MarketHotQuery("行业", "汽车之家 今日 热门车型 新能源 SUV 上市 预售 订单"),
            new MarketHotQuery("行业", "懂车帝 今日 热门车型 新车 上市 价格 试驾"),
            new MarketHotQuery("行业", "易车 今日 新能源 热门车型 上市 预售 权益"),
            new MarketHotQuery("行业", "太平洋汽车 今日 热门车型 新车 价格 订单"),
            new MarketHotQuery("行业", "盖世汽车 今日 新能源汽车 热门车型 事件"),
            new MarketHotQuery("行业", "新浪汽车 今日 新能源SUV 热门车型 新车"),
            new MarketHotQuery("行业", "搜狐汽车 今日 汽车市场 热门车型 上市 预售"),
            new MarketHotQuery("行业", "汽车垂媒 今日 热门车型 价格 权益 试驾"),
            new MarketHotQuery("行业", "近24小时 新能源汽车 热门车型 高热度 事件"),
            new MarketHotQuery("行业", "今日 新能源SUV 热门车型 市场热度 订单")
    );
    private static final Set<String> WEB_SUPPLEMENT_BENCHMARK_BRANDS = Set.of(
            "猛士", "仰望", "坦克", "方程豹", "问界", "路虎"
    );
    private static final List<String> STRATEGIC_ACTION_TOPICS = List.of(
            "技术路线 产品规划 平台架构 供应链 合作伙伴",
            "产能 工厂 投产 出海 海外 组织调整 资本 融资"
    );

    private final BochaApiClient bochaApiClient;
    private final WebSearchNewsMapper webSearchNewsMapper;
    private final BrandKeywordConfigMapper keywordMapper;
    private final NewsUrlBlacklistMapper blacklistMapper;
    private final AutohomeSeriesConfigMapper seriesConfigMapper;
    private final WebArticleContentService articleContentService;

    /** 兼容旧调用入口（参数被忽略，仅用于 DailyReportPipeline 等老代码）。 */
    public int collectAll(Map<String, List<String>> ignored) {
        return collectByCategory(null);
    }

    /**
     * 按 category 采集。
     *   category=null → 全跑
     *   category="self"/"competitor"/"industry" → 仅跑该分类
     *   兼容 "brand" → 当 "self+competitor" 处理（v1 用法）
     */
    public int collectByCategory(String category) {
        LambdaQueryWrapper<BrandKeywordConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(BrandKeywordConfig::getEnabled, true);

        if (category != null && !category.isEmpty()) {
            if ("brand".equalsIgnoreCase(category)) {
                qw.in(BrandKeywordConfig::getCategory, CATEGORY_SELF, CATEGORY_COMPETITOR);
            } else {
                qw.eq(BrandKeywordConfig::getCategory, category.toLowerCase());
            }
        }
        qw.orderByAsc(BrandKeywordConfig::getCategory)
          .orderByAsc(BrandKeywordConfig::getBrandName)
          .orderByAsc(BrandKeywordConfig::getId);

        List<BrandKeywordConfig> keywords = keywordMapper.selectList(qw);
        if (keywords.isEmpty()) {
            log.warn("[WebNewsCollector] no enabled keywords for category={}", category);
            return 0;
        }
        // 加载 URL 黑名单（每次 collect 调用都重新加载，方便后台改了立即生效）
        List<String> blockedPatterns = blacklistMapper.selectList(
                new LambdaQueryWrapper<NewsUrlBlacklist>()
                        .eq(NewsUrlBlacklist::getIsEnabled, true))
                .stream().map(NewsUrlBlacklist::getPattern).collect(Collectors.toList());
        log.info("[WebNewsCollector] start category={} keywords={} blacklist_patterns={}",
                category, keywords.size(), blockedPatterns.size());

        int totalInserted = 0;
        int totalBlocked = 0;
        for (BrandKeywordConfig kw : keywords) {
            try {
                int[] r = collectOne(kw, blockedPatterns);
                totalInserted += r[0];
                totalBlocked += r[1];
            } catch (Exception e) {
                log.warn("[WebNewsCollector] [{}/{}] '{}' 失败: {}",
                        kw.getCategory(), kw.getBrandName(), kw.getKeyword(), e.getMessage());
            }
        }
        log.info("[WebNewsCollector] done category={} inserted={} blocked_by_blacklist={}",
                category, totalInserted, totalBlocked);
        return totalInserted;
    }

    private int[] collectOne(BrandKeywordConfig kw, List<String> blockedPatterns) {
        List<Map<String, Object>> items = bochaApiClient.searchLast24h(kw.getKeyword(), 20);
        if (items == null || items.isEmpty()) return new int[]{0, 0};

        int inserted = 0;
        int blocked = 0;
        LocalDate today = LocalDate.now();
        for (Map<String, Object> item : items) {
            String urlHash = (String) item.get("urlHash");
            if (urlHash == null || urlHash.isEmpty()) continue;

            // 黑名单过滤（采集时直接跳过）
            String url = (String) item.get("url");
            if (url != null && isBlocked(url, blockedPatterns)) {
                blocked++;
                continue;
            }

            Long exists = webSearchNewsMapper.selectCount(
                    new QueryWrapper<WebSearchNews>().eq("url_hash", urlHash));
            if (exists != null && exists > 0) continue;

            WebSearchNews news = new WebSearchNews();
            news.setUrlHash(urlHash);
            news.setSourceTool("bocha");
            news.setSearchQuery(kw.getKeyword());
            news.setBrandName(kw.getBrandName());
            news.setCategory(kw.getCategory());
            news.setTitle((String) item.get("title"));
            news.setUrl((String) item.get("url"));
            news.setContent(fetchFullTextOrSnippet(news.getUrl(), (String) item.get("content")));
            news.setPublishedDate((String) item.get("publishedDate"));
            news.setCrawlDate(today);

            try {
                webSearchNewsMapper.insert(news);
                inserted++;
            } catch (Exception e) {
                // 并发去重等异常忽略
                log.debug("[WebNewsCollector] insert skipped url_hash={} err={}", urlHash, e.getMessage());
            }
        }
        log.debug("[WebNewsCollector] [{}/{}] '{}' → {} new, {} blocked",
                kw.getCategory(), kw.getBrandName(), kw.getKeyword(), inserted, blocked);
        return new int[]{inserted, blocked};
    }

    /**
     * 竞品日报补采：按「车系配置」中的品牌 + 车型搜索最近 24h 网页事件。
     * 这一路补微博官号之外的媒体/网页动态，仍入 web_search_news，后续复用事件分类与去重。
     */
    public int collectBenchmarkModelEvents() {
        List<AutohomeSeriesConfig> configs = seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .isNotNull(AutohomeSeriesConfig::getBrandName)
                        .isNotNull(AutohomeSeriesConfig::getModelName)
                        .orderByAsc(AutohomeSeriesConfig::getRole)
                        .orderByAsc(AutohomeSeriesConfig::getBrandName)
                        .orderByAsc(AutohomeSeriesConfig::getModelName));
        if (configs.isEmpty()) {
            log.warn("[WebNewsCollector] no enabled benchmark series config");
            return 0;
        }

        List<String> blockedPatterns = blacklistMapper.selectList(
                new LambdaQueryWrapper<NewsUrlBlacklist>()
                        .eq(NewsUrlBlacklist::getIsEnabled, true))
                .stream().map(NewsUrlBlacklist::getPattern).collect(Collectors.toList());

        int inserted = 0;
        int searched = 0;
        for (AutohomeSeriesConfig cfg : configs) {
            String brand = safeTrim(cfg.getBrandName());
            String model = safeTrim(cfg.getModelName());
            if (brand.isEmpty() || model.isEmpty()) continue;
            if (!isWebSupplementBenchmarkBrand(brand)) {
                log.debug("[WebNewsCollector] skip non-benchmark model for competitor report: brand={} model={}", brand, model);
                continue;
            }

            LinkedHashSet<String> queries = new LinkedHashSet<>();
            queries.add(brand + " " + model + " 汽车 今日 上市 价格 权益 试驾 营销");
            queries.add(model + " 汽车 最新消息 今日");

            for (String query : queries) {
                inserted += collectQuery(query, brand, categoryForRole(cfg.getRole()), blockedPatterns);
                searched++;
            }
        }
        log.info("[WebNewsCollector] benchmark model events searched={} inserted={}", searched, inserted);
        return inserted;
    }

    /** 市场热度补采：关注非固定对标品牌的全市场热门车型和高声量事件。 */
    public int collectMarketHotEvents() {
        List<String> blockedPatterns = blacklistMapper.selectList(
                new LambdaQueryWrapper<NewsUrlBlacklist>()
                        .eq(NewsUrlBlacklist::getIsEnabled, true))
                .stream().map(NewsUrlBlacklist::getPattern).collect(Collectors.toList());

        int inserted = 0;
        for (MarketHotQuery hot : MARKET_HOT_QUERIES) {
            inserted += collectQuery(hot.query, hot.brand, CATEGORY_INDUSTRY, blockedPatterns);
        }
        log.info("[WebNewsCollector] market hot events searched={} inserted={}", MARKET_HOT_QUERIES.size(), inserted);
        return inserted;
    }

    /** 战略动作补采：近 3 天口径，用一周 freshness + 查询词约束，后续在日报层按 72h 与战略信号二次过滤。 */
    public int collectStrategicActionEvents() {
        List<String> blockedPatterns = blacklistMapper.selectList(
                new LambdaQueryWrapper<NewsUrlBlacklist>()
                        .eq(NewsUrlBlacklist::getIsEnabled, true))
                .stream().map(NewsUrlBlacklist::getPattern).collect(Collectors.toList());

        int inserted = 0;
        int searched = 0;
        for (String brand : WEB_SUPPLEMENT_BENCHMARK_BRANDS) {
            for (String topic : STRATEGIC_ACTION_TOPICS) {
                String query = brand + " 汽车 近3天 " + topic;
                inserted += collectQuery(query, brand, categoryForStrategicBrand(brand), blockedPatterns, "oneWeek");
                searched++;
            }
        }
        log.info("[WebNewsCollector] strategic action events searched={} inserted={}", searched, inserted);
        return inserted;
    }

    private static class MarketHotQuery {
        private final String brand;
        private final String query;

        private MarketHotQuery(String brand, String query) {
            this.brand = brand;
            this.query = query;
        }
    }

    private int collectQuery(String query, String brandName, String category, List<String> blockedPatterns) {
        return collectQuery(query, brandName, category, blockedPatterns, "oneDay");
    }

    private int collectQuery(String query, String brandName, String category, List<String> blockedPatterns, String freshness) {
        List<Map<String, Object>> items = bochaApiClient.search(query, 20, freshness);
        if (items == null || items.isEmpty()) return 0;

        int inserted = 0;
        LocalDate today = LocalDate.now();
        for (Map<String, Object> item : items) {
            String urlHash = (String) item.get("urlHash");
            if (urlHash == null || urlHash.isEmpty()) continue;

            String url = (String) item.get("url");
            if (url != null && isBlocked(url, blockedPatterns)) continue;

            Long exists = webSearchNewsMapper.selectCount(
                    new QueryWrapper<WebSearchNews>().eq("url_hash", urlHash));
            if (exists != null && exists > 0) continue;

            WebSearchNews news = new WebSearchNews();
            news.setUrlHash(urlHash);
            news.setSourceTool("bocha");
            news.setSearchQuery(query);
            news.setBrandName(brandName);
            news.setCategory(category);
            news.setTitle((String) item.get("title"));
            news.setUrl(url);
            news.setContent(fetchFullTextOrSnippet(url, (String) item.get("content")));
            news.setPublishedDate((String) item.get("publishedDate"));
            news.setCrawlDate(today);

            try {
                webSearchNewsMapper.insert(news);
                inserted++;
            } catch (Exception e) {
                log.debug("[WebNewsCollector] benchmark insert skipped url_hash={} err={}", urlHash, e.getMessage());
            }
        }
        log.debug("[WebNewsCollector] benchmark query '{}' → {} new", query, inserted);
        return inserted;
    }

    /**
     * 对最近已存在的 Bocha 网页补全文。搜索去重会跳过旧 URL，因此这里负责把旧 snippet 升级成正文。
     * 返回更新条数；更新后会重置 extract_ts，日报事件抽取会基于全文重跑。
     */
    public int enrichRecentBochaFullText(long sinceMs, int limit) {
        List<WebSearchNews> rows = webSearchNewsMapper.findBochaFullTextCandidates(sinceMs, Math.max(1, limit));
        if (rows == null || rows.isEmpty()) return 0;

        int updated = 0;
        long now = System.currentTimeMillis();
        for (WebSearchNews row : rows) {
            String url = row.getUrl();
            String current = row.getContent();
            Optional<String> fullText = articleContentService.fetchArticleText(url, current);
            if (!fullText.isPresent()) continue;
            String content = fullText.get();
            if (isSameText(content, current)) continue;
            if (webSearchNewsMapper.updateFullContent(row.getId(), content, now) > 0) {
                updated++;
            }
        }
        log.info("[WebNewsCollector] bocha full-text enrich candidates={} updated={}", rows.size(), updated);
        return updated;
    }

    private String fetchFullTextOrSnippet(String url, String snippet) {
        return articleContentService.fetchArticleText(url, snippet).orElse(snippet);
    }

    private static boolean isSameText(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.replaceAll("\\s+", "");
        String y = b.replaceAll("\\s+", "");
        return x.equals(y) || (x.length() > 80 && y.contains(x)) || (y.length() > 80 && x.contains(y));
    }

    private static String categoryForRole(String role) {
        return "self".equalsIgnoreCase(role) ? CATEGORY_SELF : CATEGORY_COMPETITOR;
    }

    private static String categoryForStrategicBrand(String brand) {
        return "猛士".equals(brand) ? CATEGORY_SELF : CATEGORY_COMPETITOR;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isWebSupplementBenchmarkBrand(String brand) {
        if (brand == null || brand.trim().isEmpty()) return false;
        String normalized = brand.trim();
        for (String allowed : WEB_SUPPLEMENT_BENCHMARK_BRANDS) {
            if (normalized.contains(allowed) || allowed.contains(normalized)) return true;
        }
        return false;
    }

    private static boolean isBlocked(String url, List<String> patterns) {
        if (url == null || url.isEmpty()) return false;
        for (String p : patterns) {
            if (p != null && !p.isEmpty() && url.contains(p)) return true;
        }
        return false;
    }
}
