package com.nevinsight.report.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.collector.service.PlatformQueryService;
import com.nevinsight.collector.service.WebNewsCollectorService;
import com.nevinsight.intelligence.agent.*;
import com.nevinsight.intelligence.config.BrandConfigProperties;
import com.nevinsight.intelligence.service.MetricsCalculator;
import com.nevinsight.knowledge.service.MilvusKnowledgeService;
import com.nevinsight.model.dto.response.BuzzItemResponse;
import com.nevinsight.model.dto.response.CardTextSections;
import com.nevinsight.model.entity.core.DailyReportRecord;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.DailyReportRecordMapper;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import com.nevinsight.report.feishu.FeishuCardBuilder;
import com.nevinsight.report.feishu.FeishuPushService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportPipeline {

    private final BrandConfigProperties brandConfig;
    private final WebNewsCollectorService webNewsCollector;
    private final PlatformQueryService platformQueryService;
    private final SentimentAgent sentimentAgent;
    private final CompetitorAgent competitorAgent;
    private final SalesPitchAgent salesPitchAgent;
    private final ReportAssemblyAgent assemblyAgent;
    private final BriefingAgent briefingAgent;
    private final MetricsCalculator metricsCalculator;
    private final MilvusKnowledgeService knowledgeService;
    private final DailyReportRecordMapper reportRecordMapper;
    private final WebSearchNewsMapper webSearchNewsMapper;
    private final ObjectMapper objectMapper;
    private final FeishuCardBuilder feishuCardBuilder;
    private final FeishuPushService feishuPushService;

    @Data
    @Builder
    public static class PipelineResult {
        private boolean success;
        private CardTextSections sections;
        private MetricsCalculator.DailyKPI kpi;
        private String cardJson;
        private String error;
    }

    /**
     * 运行日报管线
     */
    public PipelineResult run(LocalDate targetDate, boolean persist, boolean push) {
        String brand = brandConfig.getDbName();
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();

        log.info("[Pipeline] 开始生成 {} {} 日报", brandConfig.getName(), date);

        try {
            // Step 0: 搜索引擎24h新闻采集
            log.info("[Step 0/6] 搜索引擎新闻采集...");
            try {
                int inserted = webNewsCollector.collectAll(brandConfig.getSearchKeywords());
                log.info("[Step 0/6] 采集完成, 新增 {} 条", inserted);
            } catch (Exception e) {
                log.warn("[Step 0/6] 采集失败(不影响后续): {}", e.getMessage());
            }

            // Step 1: 平台数据采集
            log.info("[Step 1/6] 平台数据采集...");
            List<BuzzItemResponse> hotPosts = platformQueryService.queryBuzzUnion(brand, date, null, 50, 0);

            // Step 2: 情感分析
            log.info("[Step 2/6] 情感分析...");
            List<String> contents = hotPosts.stream()
                    .map(p -> p.getTitle() + " " + p.getContent())
                    .collect(Collectors.toList());
            SentimentAgent.Output sentimentOutput = sentimentAgent.execute(new SentimentAgent.Input(contents));

            // 计算 KPI
            int[] sentimentCounts = computeSentimentCounts(sentimentOutput.getSentimentMap());
            double avgScore = metricsCalculator.computeAvgSentimentScore(
                    sentimentCounts[4], sentimentCounts[3], sentimentCounts[2],
                    sentimentCounts[1], sentimentCounts[0]);
            int total = Arrays.stream(sentimentCounts).sum();
            double negRatio = total > 0 ? (sentimentCounts[0] + sentimentCounts[1]) * 1.0 / total : 0;
            double posRatio = total > 0 ? (sentimentCounts[3] + sentimentCounts[4]) * 1.0 / total : 0;

            MetricsCalculator.DailyKPI kpi = MetricsCalculator.DailyKPI.builder()
                    .totalMentions(total)
                    .positiveRatio(posRatio)
                    .negativeRatio(negRatio)
                    .avgSentimentScore(avgScore)
                    .statusLevel(metricsCalculator.determineStatusLevel(negRatio, 0,
                            brandConfig.getRedNegativeRatio(), brandConfig.getYellowNegativeRatio()))
                    .veryPositiveCount(sentimentCounts[4])
                    .positiveCount(sentimentCounts[3])
                    .neutralCount(sentimentCounts[2])
                    .negativeCount(sentimentCounts[1])
                    .veryNegativeCount(sentimentCounts[0])
                    .build();

            // Step 3: 竞品分析 + 话术生成 (并行)
            log.info("[Step 3/6] 竞品分析 + 话术生成 (并行)...");
            String knowledgeContext = knowledgeService.searchForRag(brandConfig.getName() + " 竞品对标 话术");

            CompletableFuture<CompetitorAgent.Output> compFuture = CompletableFuture.supplyAsync(() ->
                    competitorAgent.execute(new CompetitorAgent.Input("", "", "")));
            CompletableFuture<SalesPitchAgent.Output> pitchFuture = CompletableFuture.supplyAsync(() ->
                    salesPitchAgent.execute(new SalesPitchAgent.Input("", "", "", knowledgeContext)));

            CompetitorAgent.Output compResult;
            SalesPitchAgent.Output pitchResult;
            try {
                compResult = compFuture.get(120, TimeUnit.SECONDS);
                pitchResult = pitchFuture.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[Step 3/6] Agent超时，使用降级: {}", e.getMessage());
                compResult = competitorAgent.fallback(null, e);
                pitchResult = salesPitchAgent.fallback(null, e);
            }

            // Step 4: 日报汇总
            log.info("[Step 4/6] 日报汇总生成...");
            // P1：从 DB 直接拼装的三块（非 LLM）
            List<CardTextSections.CoreNewsItem> coreNews = buildCoreNews(brand, date, 5);
            List<CardTextSections.IndustryHotspot> industryHotspots = buildIndustryHotspots(date, 3);
            List<CardTextSections.HotDiscussion> hotDiscussions =
                    buildHotDiscussions(hotPosts, sentimentOutput.getSentimentMap(), 5);

            // Step 4.5: 7 日均值 + 速览 + 议题聚类 + 危机预警
            HistoricalKpi avg7 = computeRecentAvgKpi(brand, date, 7);
            BriefingAgent.Output briefing = runBriefingAgent(
                    brandConfig.getName(), kpi, avg7,
                    hotPosts, sentimentOutput.getSentimentMap());
            List<String> alerts = computeCrisisAlerts(
                    kpi, avg7, hotPosts, sentimentOutput.getSentimentMap());

            CardTextSections sections = CardTextSections.builder()
                    .coreNews(coreNews)
                    .hotDiscussions(hotDiscussions)
                    .industryHotspots(industryHotspots)
                    .competitorTracking(compResult.getCompetitorTracking())
                    .competitorBenchmark(compResult.getCompetitorBenchmark())
                    .suggestedTalkingPoints(pitchResult.getTalkingPoints())
                    .dailyBriefing(briefing.getBriefing())
                    .topics(briefing.getTopics())
                    .crisisAlerts(alerts)
                    .build();

            // Step 5: 构建飞书卡片 + 持久化
            // 趋势数据：取近 7 天的 daily_report_record（不含今天的，因为今天的还没存）
            List<FeishuCardBuilder.TrendPoint> trend = buildTrend(brand, date, 7);
            String detailUrl = "http://localhost:3080/daily-report"; // TODO: 改用配置项
            String cardJson = feishuCardBuilder.build(brandConfig.getName(), date, kpi, sections, trend, detailUrl);
            if (persist) {
                log.info("[Step 5/6] 持久化...");
                persistReport(brand, date, sections, kpi, cardJson);
            }

            // Step 6: 推送飞书
            if (push) {
                log.info("[Step 6/6] 推送飞书...");
                boolean pushed = feishuPushService.sendToProduction(cardJson);
                log.info("[Step 6/6] 推送结果: {}", pushed ? "成功" : "失败");
            }

            log.info("[Pipeline] {} {} 日报生成完成", brandConfig.getName(), date);
            return PipelineResult.builder()
                    .success(true)
                    .sections(sections)
                    .kpi(kpi)
                    .cardJson(cardJson)
                    .build();

        } catch (Exception e) {
            log.error("[Pipeline] 日报生成失败: {}", e.getMessage(), e);
            return PipelineResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    @Data
    @Builder
    static class HistoricalKpi {
        double total;
        double posRatio;
        double negRatio;
        double avgScore;  // 1-5
        int sampleDays;
    }

    /** 取近 N 天日报记录(不含当天)的 KPI 平均值 */
    private HistoricalKpi computeRecentAvgKpi(String brand, LocalDate today, int days) {
        try {
            List<DailyReportRecord> rows = reportRecordMapper.findRecentByBrand(brand, today, days);
            double sumT = 0, sumP = 0, sumN = 0, sumS = 0;
            int n = 0;
            for (DailyReportRecord r : rows) {
                if (r.getReportDate() == null || r.getReportDate().isEqual(today)) continue;
                try {
                    Map<String, Object> kpi = objectMapper.readValue(
                            r.getKpiJson() == null ? "{}" : r.getKpiJson(), Map.class);
                    Object t = kpi.get("totalMentions");
                    Object p = kpi.get("positiveRatio");
                    Object ng = kpi.get("negativeRatio");
                    Object s = kpi.get("avgSentimentScore");
                    if (t instanceof Number) sumT += ((Number) t).doubleValue();
                    if (p instanceof Number) sumP += ((Number) p).doubleValue();
                    if (ng instanceof Number) sumN += ((Number) ng).doubleValue();
                    if (s instanceof Number) sumS += ((Number) s).doubleValue();
                    n++;
                } catch (Exception ignored) {}
            }
            if (n == 0) return HistoricalKpi.builder().total(0).posRatio(0).negRatio(0).avgScore(3.0).sampleDays(0).build();
            return HistoricalKpi.builder()
                    .total(sumT / n)
                    .posRatio(sumP / n)
                    .negRatio(sumN / n)
                    .avgScore(sumS / n)
                    .sampleDays(n).build();
        } catch (Exception e) {
            log.warn("[Pipeline] 7 日均值加载失败: {}", e.getMessage());
            return HistoricalKpi.builder().total(0).posRatio(0).negRatio(0).avgScore(3.0).sampleDays(0).build();
        }
    }

    /** 调用 BriefingAgent 生成速览+议题聚类 */
    private BriefingAgent.Output runBriefingAgent(
            String brandName,
            MetricsCalculator.DailyKPI kpi,
            HistoricalKpi avg7,
            List<BuzzItemResponse> hotPosts,
            Map<Integer, String> sentimentMap) {
        try {
            int sample = Math.min(15, hotPosts.size());
            List<BriefingAgent.PostRef> refs = new ArrayList<>();
            for (int i = 0; i < sample; i++) {
                BuzzItemResponse p = hotPosts.get(i);
                BriefingAgent.PostRef r = new BriefingAgent.PostRef();
                r.setPlatform(p.getPlatformName() == null ? "" : p.getPlatformName());
                r.setTitle(p.getTitle() == null ? "" : p.getTitle());
                r.setContent(p.getContent() == null ? "" : p.getContent());
                r.setUrl(p.getUrl() == null ? "" : p.getUrl());
                r.setSentimentTag(sentimentMap == null ? "中性" : sentimentMap.getOrDefault(i, "中性"));
                r.setEngagement(p.getEngagementScore());
                refs.add(r);
            }
            BriefingAgent.Input in = new BriefingAgent.Input();
            in.setBrand(brandName);
            in.setTodayTotal(kpi.getTotalMentions());
            in.setTodayPosRatio(kpi.getPositiveRatio());
            in.setTodayNegRatio(kpi.getNegativeRatio());
            in.setTodayHealth(kpi.getAvgSentimentScore());
            in.setAvg7Total(avg7.total);
            in.setAvg7PosRatio(avg7.posRatio);
            in.setAvg7NegRatio(avg7.negRatio);
            in.setAvg7Health(avg7.avgScore);
            in.setPosts(refs);
            return briefingAgent.execute(in);
        } catch (Exception e) {
            log.warn("[Pipeline] 速览/议题生成失败: {}", e.getMessage());
            return briefingAgent.fallback(null, e);
        }
    }

    /** 危机预警：负面突增 / 高互动负面帖 / 总声量突增 */
    private List<String> computeCrisisAlerts(
            MetricsCalculator.DailyKPI kpi,
            HistoricalKpi avg7,
            List<BuzzItemResponse> hotPosts,
            Map<Integer, String> sentimentMap) {
        List<String> alerts = new ArrayList<>();
        if (avg7.sampleDays >= 1) {
            double negDelta = kpi.getNegativeRatio() - avg7.negRatio;
            if (negDelta >= 0.10) {
                alerts.add(String.format(
                        "负面占比 %.1f%%（7 日均值 %.1f%%，**+%.1fpp**）",
                        kpi.getNegativeRatio() * 100, avg7.negRatio * 100, negDelta * 100));
            }
            if (avg7.total > 0 && kpi.getTotalMentions() >= avg7.total * 1.5) {
                alerts.add(String.format(
                        "声量突增：今日 %d 条，7 日均值 %.0f 条（**+%.0f%%**）",
                        kpi.getTotalMentions(), avg7.total,
                        (kpi.getTotalMentions() / avg7.total - 1) * 100));
            }
        }
        if (hotPosts != null && sentimentMap != null) {
            for (int i = 0; i < hotPosts.size() && i < 20; i++) {
                String tag = sentimentMap.getOrDefault(i, "中性");
                if (!(tag.contains("负面"))) continue;
                BuzzItemResponse p = hotPosts.get(i);
                long eng = p.getEngagementScore();
                if (eng < 50000) continue;
                String t = p.getTitle();
                if (t == null || t.isEmpty()) t = p.getContent();
                if (t != null && t.length() > 40) t = t.substring(0, 40) + "...";
                String url = p.getUrl();
                String linked = (url != null && !url.isEmpty())
                        ? String.format("[%s](%s)", t, url) : (t == null ? "" : t);
                alerts.add(String.format("高互动负面帖（互动 %s）：%s", humanNumberShort(eng), linked));
                if (alerts.size() >= 5) break;
            }
        }
        return alerts;
    }

    private static String humanNumberShort(long n) {
        if (n >= 10000) return String.format("%.1f万", n / 10000.0);
        return String.valueOf(n);
    }

    /** 取近 N 天日报记录构造趋势点（含当日，按日期升序——最新在右） */
    private List<FeishuCardBuilder.TrendPoint> buildTrend(String brand, LocalDate date, int days) {
        try {
            List<DailyReportRecord> rows = reportRecordMapper.findRecentByBrand(brand, date, days);
            // mapper 返回降序，反转为升序
            Collections.reverse(rows);
            List<FeishuCardBuilder.TrendPoint> out = new ArrayList<>();
            for (DailyReportRecord r : rows) {
                int total = 0;
                double score = 3.0;
                try {
                    Map<String, Object> kpi = objectMapper.readValue(
                            r.getKpiJson() == null ? "{}" : r.getKpiJson(), Map.class);
                    Object t = kpi.get("totalMentions");
                    if (t instanceof Number) total = ((Number) t).intValue();
                    Object s = kpi.get("avgSentimentScore");
                    if (s instanceof Number) score = ((Number) s).doubleValue();
                } catch (Exception ignored) { }
                String label = r.getReportDate().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"));
                out.add(new FeishuCardBuilder.TrendPoint(label, total, score));
            }
            return out;
        } catch (Exception e) {
            log.warn("[Pipeline] 趋势数据加载失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 取该品牌 36 小时内采集到的新闻 TOP N（必须含品牌关键词，按相关度→正文长度→id 倒序） */
    private List<CardTextSections.CoreNewsItem> buildCoreNews(String brand, LocalDate date, int limit) {
        try {
            long thirtySixHoursAgoMs = System.currentTimeMillis() - 36L * 3600 * 1000;
            QueryWrapper<WebSearchNews> qw = new QueryWrapper<>();
            qw.eq("brand_name", brand)
              .eq("category", "brand")
              .ge("add_ts", thirtySixHoursAgoMs)
              .and(w -> w.like("title", brand).or().like("content", brand))
              .last("ORDER BY relevance_score IS NULL, relevance_score DESC, "
                    + "CHAR_LENGTH(content) DESC, id DESC LIMIT " + Math.max(1, limit));
            List<WebSearchNews> rows = webSearchNewsMapper.selectList(qw);
            Map<Integer, String> sentimentMap = classifyNewsSentiment(rows);
            List<CardTextSections.CoreNewsItem> out = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                WebSearchNews n = rows.get(i);
                out.add(CardTextSections.CoreNewsItem.builder()
                        .tag("品牌")
                        .sentiment(sentimentMap.getOrDefault(i, "中性"))
                        .title(n.getTitle())
                        .analysis(truncate(n.getContent(), 80))
                        .sourceUrl(n.getUrl())
                        .build());
            }
            return out;
        } catch (Exception e) {
            log.warn("[Pipeline] 核心资讯加载失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 对核心资讯标题+正文调用 SentimentAgent 做情感分类（失败兜底中性） */
    private Map<Integer, String> classifyNewsSentiment(List<WebSearchNews> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyMap();
        List<String> contents = new ArrayList<>(rows.size());
        for (WebSearchNews n : rows) {
            String t = n.getTitle() == null ? "" : n.getTitle();
            String c = n.getContent() == null ? "" : n.getContent();
            contents.add((t + " " + c).trim());
        }
        try {
            SentimentAgent.Output res = sentimentAgent.execute(new SentimentAgent.Input(contents));
            return res.getSentimentMap();
        } catch (Exception e) {
            log.warn("[Pipeline] 核心资讯情感分析失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 取行业新闻 TOP N */
    private List<CardTextSections.IndustryHotspot> buildIndustryHotspots(LocalDate date, int limit) {
        try {
            long thirtySixHoursAgoMs = System.currentTimeMillis() - 36L * 3600 * 1000;
            QueryWrapper<WebSearchNews> qw = new QueryWrapper<>();
            qw.eq("category", "industry")
              .ge("add_ts", thirtySixHoursAgoMs)
              .last("ORDER BY relevance_score IS NULL, relevance_score DESC, "
                    + "CHAR_LENGTH(content) DESC, id DESC LIMIT " + Math.max(1, limit));
            List<WebSearchNews> rows = webSearchNewsMapper.selectList(qw);
            List<CardTextSections.IndustryHotspot> out = new ArrayList<>();
            for (WebSearchNews n : rows) {
                out.add(CardTextSections.IndustryHotspot.builder()
                        .type("行业")
                        .content(n.getTitle())
                        .sourceUrl(n.getUrl())
                        .build());
            }
            return out;
        } catch (Exception e) {
            log.warn("[Pipeline] 行业热点加载失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 从已抓取的 hotPosts 取互动量 TOP N，附情感标签 */
    private List<CardTextSections.HotDiscussion> buildHotDiscussions(
            List<BuzzItemResponse> hotPosts,
            Map<Integer, String> sentimentMap,
            int limit) {
        if (hotPosts == null || hotPosts.isEmpty()) return Collections.emptyList();
        // hotPosts 已按 engagement_score 降序，直接取前 N
        List<CardTextSections.HotDiscussion> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, hotPosts.size()); i++) {
            BuzzItemResponse p = hotPosts.get(i);
            String tag = sentimentMap != null ? sentimentMap.getOrDefault(i, "中性") : "中性";
            String stats = String.format("👍%s · 💬%s · 互动%d",
                    safeNum(p.getLikedCount()), safeNum(p.getCommentCount()), p.getEngagementScore());
            String title = (p.getTitle() != null && !p.getTitle().isEmpty()) ? p.getTitle() : p.getContent();
            out.add(CardTextSections.HotDiscussion.builder()
                    .sentimentTag(tag)
                    .platform(p.getPlatformName())
                    .author(p.getNickname())
                    .title(truncate(title, 60))
                    .stats(stats)
                    .comment(truncate(p.getContent(), 100))
                    .sourceUrl(p.getUrl())
                    .build());
        }
        return out;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private String safeNum(String s) {
        return (s == null || s.isEmpty() || "null".equals(s)) ? "0" : s;
    }

    private int[] computeSentimentCounts(Map<Integer, String> sentimentMap) {
        int[] counts = new int[5]; // [veryNeg, neg, neutral, pos, veryPos]
        for (String sentiment : sentimentMap.values()) {
            String s = sentiment == null ? "" : sentiment.trim();
            // 注意顺序：必须先匹配"非常..."否则会被"正面"/"负面"短匹配吃掉
            if (s.contains("非常正面") || s.contains("非常正向")) counts[4]++;
            else if (s.contains("非常负面") || s.contains("非常负向")) counts[0]++;
            else if (s.contains("正面") || s.contains("正向") || s.contains("积极")) counts[3]++;
            else if (s.contains("负面") || s.contains("负向") || s.contains("消极")) counts[1]++;
            else counts[2]++; // 中性 / 未识别
        }
        log.info("[Pipeline] 情感分布: very-={} -={} 0={} +={} very+={} (total={})",
                counts[0], counts[1], counts[2], counts[3], counts[4], sentimentMap.size());
        return counts;
    }

    private void persistReport(String brand, LocalDate date, CardTextSections sections,
                               MetricsCalculator.DailyKPI kpi, String cardJson) {
        try {
            DailyReportRecord existing = reportRecordMapper.findByBrandAndDate(brand, date);
            DailyReportRecord record = existing != null ? existing : new DailyReportRecord();
            record.setBrandName(brand);
            record.setReportDate(date);
            record.setSectionsJson(objectMapper.writeValueAsString(sections));
            record.setKpiJson(objectMapper.writeValueAsString(kpi));
            record.setCardJson(cardJson != null ? cardJson : "{}");
            record.setStatus("draft");

            if (existing != null) {
                reportRecordMapper.updateById(record);
            } else {
                reportRecordMapper.insert(record);
            }
        } catch (Exception e) {
            log.error("[Pipeline] 持久化失败: {}", e.getMessage());
        }
    }
}
