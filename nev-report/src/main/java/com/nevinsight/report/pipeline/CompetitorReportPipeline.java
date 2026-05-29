package com.nevinsight.report.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.collector.service.WebNewsCollectorService;
import com.nevinsight.intelligence.agent.CardGroupSummaryAgent;
import com.nevinsight.intelligence.agent.CardGroupSummaryAgent.CardGroupSummary;
import com.nevinsight.intelligence.agent.CardGroupSummaryAgent.GroupSummaryInput;
import com.nevinsight.intelligence.agent.CardInsightAgent;
import com.nevinsight.intelligence.agent.CardInsightAgent.CardInsight;
import com.nevinsight.intelligence.agent.CardInsightAgent.CardInsightInput;
import com.nevinsight.intelligence.agent.CompetitorAgent;
import com.nevinsight.intelligence.client.RagflowClient;
import com.nevinsight.intelligence.config.BrandConfigProperties;
import com.nevinsight.intelligence.config.RagflowProperties;
import com.nevinsight.intelligence.service.NewsEventExtractionService;
import com.nevinsight.model.dto.response.CardTextSections;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.AutohomeSpec;
import com.nevinsight.model.entity.core.GasgooSalesRecord;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.AutohomeSpecMapper;
import com.nevinsight.model.mapper.core.GasgooSalesRecordMapper;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import com.nevinsight.report.feishu.CompetitorCardBuilder;
import com.nevinsight.report.feishu.FeishuPushService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 竞品分析日报管线 v2：新闻驱动。
 *
 * 5 步：
 *  1. SQL 取 36h 内三类新闻（self/competitor/industry）
 *  2. SQL 拼 autohome_spec 对标矩阵 (无 LLM 解读)
 *  3. CompetitorAgent.summarizeBriefing 仅做 1-2 句速览
 *  4. CompetitorAgent.runTalkingPointsOnly 用 RAGFlow 出销售话术（暂保留）
 *  5. CompetitorCardBuilder 构卡 → 推送独立 webhook
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorReportPipeline {

    /** 36h 窗口（毫秒） */
    private static final long WINDOW_MS = 36L * 3600 * 1000;
    /** 战略动作看近 3 天。 */
    private static final long STRATEGIC_WINDOW_MS = 72L * 3600 * 1000;

    /** v1 默认 6 个人群（与 docs/客户人群画像-模板.xlsx 对应） */
    public static final List<CompetitorAgent.Persona> DEFAULT_PERSONAS = List.of(
            new CompetitorAgent.Persona("P01", "硬派越野玩家"),
            new CompetitorAgent.Persona("P02", "都市精英家庭"),
            new CompetitorAgent.Persona("P03", "越野俱乐部 KOL"),
            new CompetitorAgent.Persona("P04", "价格敏感型实用派"),
            new CompetitorAgent.Persona("P05", "比亚迪意向客户"),
            new CompetitorAgent.Persona("P06", "理想/问界意向客户")
    );

    /** 对标矩阵保留的关键参数项前缀 */
    private static final List<String> KEY_PARAM_NAMES = List.of(
            "厂商指导价", "厂商", "级别", "能源类型",
            "上市时间", "最大功率", "最大扭矩",
            "百公里加速", "纯电续航", "WLTC纯电续航",
            "驱动方式", "电池容量", "充电时间", "整备质量",
            "长×宽×高", "轴距", "差速锁",
            "接近角", "离去角", "最大涉水"
    );

    private final CompetitorAgent competitorAgent;
    private final AutohomeSeriesConfigMapper seriesConfigMapper;
    private final AutohomeSpecMapper specMapper;
    private final WebSearchNewsMapper newsMapper;
    private final GasgooSalesRecordMapper gasgooMapper;
    private final CompetitorCardBuilder cardBuilder;
    private final FeishuPushService feishuPushService;
    private final BrandConfigProperties brandConfig;
    private final RagflowClient ragflowClient;
    private final RagflowProperties ragflowProperties;
    private final WebNewsCollectorService webNewsCollectorService;
    private final NewsEventExtractionService eventExtractionService;
    private final CardInsightAgent cardInsightAgent;
    private final CardGroupSummaryAgent cardGroupSummaryAgent;

    /** 飞书按钮跳转的 m-monitor 前端深度对标页 URL */
    @Value("${nevinsight.frontend.base-url:http://localhost:3080}")
    private String frontendBaseUrl;

    /** v3 4 类事件 + 每类拉取上限（去重后实际可能少很多）。 */
    private static final String[] EVENT_TYPES_ORDER = {"launch", "price_finance", "campaign", "sales_milestone"};
    private static final Map<String, Integer> EVENT_TYPE_LIMITS = Map.of(
            "launch",          30,
            "price_finance",   30,
            "campaign",        30,
            "sales_milestone", 30
    );

    /** 事件去重的前缀长度（按 event_summary 前 N 字符判断重复） */
    private static final int DEDUP_PREFIX_LEN = 25;

    /** 二级模糊去重：同 brand + 同 event_type 内，bigram Jaccard 相似度 ≥ 此阈值视为同事件
     *  实测 0.4 太严（同事件不同媒体改写共用 bigram ≈ 25%），0.25 平衡去重精度和误伤率 */
    private static final double DEDUP_JACCARD_THRESHOLD = 0.25;

    /** Bigram 黑名单：泛用词不参与去重计算（避免「正式」「上市」「发布」类高频词撑高相似度） */
    private static final Set<String> STOP_BIGRAMS = Set.of(
            "正式", "式上", "上市", "发布", "宣布", "开启", "将于", "举行", "重磅",
            "之前", "之后", "目前", "刚刚", "今日", "今天", "汽车", "车型",
            "系列", "推出", "新款", "全新", "提供", "首搭", "搭载");

    /** 对标品牌白名单 + 渲染顺序（本品在前，竞品按对标关系排）。其他品牌（行业、阿维塔等）的事件不上卡。 */
    private static final List<String> BENCHMARK_BRANDS_ORDER = List.of(
            "猛士", "仰望", "坦克", "方程豹", "问界", "路虎");
    private static final Map<String, Integer> BRAND_ORDER_INDEX;
    static {
        BRAND_ORDER_INDEX = new LinkedHashMap<>();
        for (int i = 0; i < BENCHMARK_BRANDS_ORDER.size(); i++) {
            BRAND_ORDER_INDEX.put(BENCHMARK_BRANDS_ORDER.get(i), i);
        }
    }

    /** v7：销量板块额外允许的行业权威品牌（乘联会/中汽协 报的数据虽然 brand=「乘联会/中汽协」，
     *  但仍是销量真理之源，不该被对标白名单一刀切）。这些 brand 仅在销量板块放行。 */
    private static final Set<String> SALES_AUTHORITY_BRANDS = Set.of("乘联会", "中汽协");

    public Result run(LocalDate targetDate, boolean push) {
        return run(targetDate, push, true);
    }

    public Result run(LocalDate targetDate, boolean push, boolean supplementWebNews) {
        LocalDate date = targetDate != null ? targetDate : LocalDate.now();
        long sinceMs = System.currentTimeMillis() - WINDOW_MS;
        log.info("[CompetitorPipeline] v3 start date={} window=36h since={}", date, sinceMs);

        // ===== Step 0: 微博之外的网页补采 =====
        // 按车系配置里的品牌+车型搜索近 24h 汽车事件，入 web_search_news 后复用事件分类。
        if (supplementWebNews) {
            try {
                int inserted = webNewsCollectorService.collectBenchmarkModelEvents();
                inserted += webNewsCollectorService.collectMarketHotEvents();
                inserted += webNewsCollectorService.collectStrategicActionEvents();
                int fullTextUpdated = webNewsCollectorService.enrichRecentBochaFullText(sinceMs, 80);
                if (inserted > 0 || fullTextUpdated > 0) {
                    NewsEventExtractionService.Result extracted = eventExtractionService.extractPending(20);
                    log.info("[CompetitorPipeline] benchmark web supplement inserted={} fullTextUpdated={} extracted={}/{} batches={}",
                            inserted, fullTextUpdated, extracted.success, extracted.processed, extracted.batchesRun);
                } else {
                    log.info("[CompetitorPipeline] benchmark web supplement no new rows");
                }
            } catch (Exception e) {
                log.warn("[CompetitorPipeline] benchmark web supplement failed ignored: {}", e.getMessage());
            }
        } else {
            log.info("[CompetitorPipeline] benchmark web supplement skipped by request");
        }

        // ===== Step 1: 按 event_type 拉事件 + 仅保留对标品牌 + 去重 + 按品牌排序 =====
        // v7: sales_milestone 走专路径 — 每品牌最新一条官号一手数据（不限 36h）
        // v8+: price_finance 聚合所有含价格信息的官号帖（含 launch 中带价格的）；launch 排除已进 price 的 id
        Map<String, List<WebSearchNews>> byEvent = new LinkedHashMap<>();
        Map<String, FilterAudit> filterAudit = new LinkedHashMap<>();
        Set<Long> priceFinanceIds = new HashSet<>();
        int totalEvents = 0;
        // 第一遍：先处理 price_finance，记录已用 id；再处理 launch 时排除
        List<String> orderedTypes = new ArrayList<>();
        orderedTypes.add("price_finance");  // 先处理拿到去重集
        for (String et : EVENT_TYPES_ORDER) if (!"price_finance".equals(et)) orderedTypes.add(et);
        long strategicSinceMs = System.currentTimeMillis() - STRATEGIC_WINDOW_MS;

        for (String et : orderedTypes) {
            int limit = EVENT_TYPE_LIMITS.getOrDefault(et, 30);
            List<WebSearchNews> rows;
            List<WebSearchNews> rawRows;
            if ("strategic_action".equals(et)) {
                List<WebSearchNews> rawStrategic = newsMapper.findStrategicActionCandidates(strategicSinceMs, limit);
                rows = rawStrategic.stream()
                        .filter(CompetitorReportPipeline::hasStrategicActionSignal)
                        .collect(Collectors.toList());
                rawRows = rawStrategic;
                log.info("[CompetitorPipeline] strategic_action raw={} filtered={} rows", rawStrategic.size(), rows.size());
            } else if ("sales_milestone".equals(et)) {
                rows = newsMapper.findLatestAuthoritativeSalesByBrand();
                rawRows = rows;
                log.info("[CompetitorPipeline] sales_milestone 走权威路径，raw={} 条", rows.size());
            } else if ("price_finance".equals(et)) {
                // v9: 只保留明确权益/金融政策，纯售价/上市定价不挪入价格金融。
                List<WebSearchNews> rawFinance = newsMapper.findFinanceCandidates(sinceMs, limit);
                rows = rawFinance.stream()
                        .filter(CompetitorReportPipeline::hasStrongFinancePolicySignal)
                        .collect(Collectors.toList());
                rawRows = rawFinance;
                log.info("[CompetitorPipeline] price_finance strong-policy raw={} filtered={} 条",
                        rawFinance.size(), rows.size());
            } else if ("launch".equals(et)) {
                // 产品动态板块：微博官号/官号优先；官号为空或不足时，网页补采作为兜底。
                rows = newsMapper.findProductEventsOfficialFirst(sinceMs, limit).stream()
                        .filter(n -> !priceFinanceIds.contains(n.getId()))
                        .collect(Collectors.toList());
                rawRows = rows;
                log.info("[CompetitorPipeline] product dynamics official-first 排除已进 price_finance 后 raw={} 条", rows.size());
            } else if ("campaign".equals(et)) {
                rows = newsMapper.findByEventTypeOfficialFirst(et, sinceMs, limit);
                rawRows = rows;
            } else {
                rows = newsMapper.findByEventTypeOfficialFirst(et, sinceMs, limit);
                rawRows = rows;
            }
            List<WebSearchNews> filtered = filterByBenchmarkBrands(rows, et);
            List<WebSearchNews> deduped = "sales_milestone".equals(et)
                    ? filtered
                    : dedupBySimilarity(dedupBySummary(filtered));
            List<WebSearchNews> sorted = sortByBrandThenImportance(deduped);
            if (!"sales_milestone".equals(et)) {
                FilterAudit audit = FilterAudit.builder()
                        .rawCount(rawRows == null ? 0 : rawRows.size())
                        .brandFilteredCount(filtered.size())
                        .dedupedCount(deduped.size())
                        .displayedCount(sorted.size())
                        .samples(new ArrayList<>())
                        .build();
                addDroppedSamples(audit, rawRows, rows, "policy_or_pre_dedup_filter");
                addDroppedSamples(audit, rows, filtered, "brand_not_in_benchmark");
                addDroppedSamples(audit, filtered, deduped, "dedup_by_summary_or_similarity");
                filterAudit.put(et, audit);
            }
            byEvent.put(et, sorted);
            totalEvents += sorted.size();
            // 把 price_finance 渲染出的 id 记下，给 launch 阶段去重
            if ("price_finance".equals(et)) {
                sorted.forEach(n -> priceFinanceIds.add(n.getId()));
            }
        }
        log.info("[CompetitorPipeline] events (after dedup): launch={} price_finance={} campaign={} sales_milestone={} strategic_action={} (total={})",
                byEvent.get("launch").size(), byEvent.get("price_finance").size(),
                byEvent.get("campaign").size(), byEvent.get("sales_milestone").size(),
                byEvent.getOrDefault("strategic_action", Collections.emptyList()).size(), totalEvents);

        List<WebSearchNews> rawMarketHot = newsMapper.findMarketHotEvents(sinceMs, 40);
        List<WebSearchNews> marketHotEvents = dedupBySimilarity(dedupBySummary(
                rawMarketHot.stream()
                        .filter(CompetitorReportPipeline::hasMarketHotSignal)
                        .collect(Collectors.toList())));
        log.info("[CompetitorPipeline] market_hot raw={} filtered={} rows",
                rawMarketHot.size(), marketHotEvents.size());

        // ===== Step 1.5: 盖世销量结构化数据 — 每车型最新一条 + 按 vs_self_model 分组 =====
        // 构造 model_name → vs_self_model 映射（self 行的 vs_self_model 是 NULL，用自己 model_name 兜底）
        Map<String, String> modelToVsSelf = new LinkedHashMap<>();
        for (AutohomeSeriesConfig cfg : seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true))) {
            String vs = cfg.getVsSelfModel();
            if (vs == null || vs.isEmpty()) vs = cfg.getModelName(); // self 自成一组
            modelToVsSelf.put(cfg.getModelName(), vs);
        }

        // 按 vs_self_model 分组（LinkedHashMap 保 self 出现顺序：猛士M817、猛士917）
        LinkedHashMap<String, List<GasgooSalesRecord>> gasgooSalesByVs = new LinkedHashMap<>();
        List<GasgooSalesRecord> allGasgoo = gasgooMapper.findLatestByModel().stream()
                .filter(r -> BRAND_ORDER_INDEX.containsKey(r.getBrandName()))
                .collect(Collectors.toList());
        // 先放 self 占位（用车系配置里 self 的出现顺序作为渲染顺序）
        for (Map.Entry<String, String> e : modelToVsSelf.entrySet()) {
            if (e.getKey().equals(e.getValue())) {
                gasgooSalesByVs.putIfAbsent(e.getValue(), new ArrayList<>());
            }
        }
        // 把每条记录扔到对应 vs 组
        for (GasgooSalesRecord r : allGasgoo) {
            String vs = modelToVsSelf.getOrDefault(r.getModelName(), r.getModelName());
            gasgooSalesByVs.computeIfAbsent(vs, k -> new ArrayList<>()).add(r);
        }
        // 组内排序：本品（model_name == vs）放第一位，其他按销量 DESC
        for (List<GasgooSalesRecord> list : gasgooSalesByVs.values()) {
            list.sort((a, b) -> {
                String vsA = modelToVsSelf.getOrDefault(a.getModelName(), a.getModelName());
                String vsB = modelToVsSelf.getOrDefault(b.getModelName(), b.getModelName());
                boolean selfA = a.getModelName().equals(vsA);
                boolean selfB = b.getModelName().equals(vsB);
                if (selfA != selfB) return selfA ? -1 : 1;
                int sA = a.getSalesCount() == null ? 0 : a.getSalesCount();
                int sB = b.getSalesCount() == null ? 0 : b.getSalesCount();
                return Integer.compare(sB, sA);
            });
        }
        log.info("[CompetitorPipeline] gasgoo sales by vs: {} 组（{} 车型）",
                gasgooSalesByVs.size(), allGasgoo.size());

        // 兼容旧 builder 签名：拍平为 list 备用
        List<GasgooSalesRecord> gasgooSales = gasgooSalesByVs.values().stream()
                .flatMap(List::stream).collect(Collectors.toList());

        // v8+: 「🎯 按对标车型最新金融政策」板块已删（重复内容），改为聚合到 💰 板块。
        // 前端 /competitor/finance-by-model 页仍保留供独立查询使用，但飞书卡片不渲染。
        LinkedHashMap<String, List<WebSearchNews>> financeByVs = new LinkedHashMap<>();

        // ===== Step 2: 对标矩阵（按 vs_self_model 分多桌） =====
        List<BenchmarkMatrix> matrices = buildBenchmarkMatrices();
        log.info("[CompetitorPipeline] benchmark: {} 桌", matrices.size());
        for (BenchmarkMatrix m : matrices) {
            log.info("  [{}] {} models × {} params", m.selfModel, m.modelNames.size(), m.paramRows.size());
        }

        // ===== Step 3: 速览（用 event_summary 喂 LLM）=====
        String briefing = "";
        if (totalEvents > 0) {
            try {
                briefing = competitorAgent.summarizeEvents(byEvent);
            } catch (Exception e) {
                log.warn("[CompetitorPipeline] briefing LLM failed: {}", e.getMessage());
            }
        } else {
            briefing = "近 36 小时无已分类的高价值事件 — 检查事件分类任务是否运行（POST /api/v1/collector/extract-events）。";
        }
        log.info("[CompetitorPipeline] briefing: {}",
                briefing.length() > 120 ? briefing.substring(0, 120) + "…" : briefing);

        // ===== Step 4: 销售话术（v5 移除：用户明确不要这板块；省 LLM token）=====
        List<CardTextSections.SuggestedTalkingPoint> talkingPoints = Collections.emptyList();

        // ===== Step 4.5: 卡片要点 LLM 提炼（本次生成内缓存，失败降级到规则兜底）=====
        Map<Long, CardInsight> cardInsights = buildCardInsights(byEvent, marketHotEvents);
        log.info("[CompetitorPipeline] card insights={}", cardInsights.size());
        enrichFilterAuditWithInsights(filterAudit, byEvent, cardInsights);
        Map<String, CardGroupSummary> groupSummaries = buildCardGroupSummaries(byEvent, cardInsights);
        log.info("[CompetitorPipeline] card group summaries={}", groupSummaries.size());

        // ===== Step 5: 构卡 + 推送 =====
        String deepAnalysisUrl = frontendBaseUrl + "/competitor/deep-analysis";
        String cardJson = cardBuilder.build(
                brandConfig.getName(), date,
                briefing,
                byEvent,
                matrices,
                talkingPoints,
                deepAnalysisUrl,
                frontendBaseUrl,
                gasgooSalesByVs,
                financeByVs,
                modelToVsSelf,
                marketHotEvents,
                cardInsights,
                groupSummaries);

        boolean pushed = false;
        if (push) {
            pushed = feishuPushService.sendCompetitorCardByAppBot(cardJson);
        }
        log.info("[CompetitorPipeline] done push={} success={}", push, pushed);

        int totalBenchmarkModels = matrices.stream().mapToInt(m -> m.modelNames.size()).sum();
        int totalBenchmarkParams = matrices.stream().mapToInt(m -> m.paramRows.size()).sum();
        return Result.builder()
                .success(true)
                .cardJson(cardJson)
                .briefing(briefing)
                .launchCount(byEvent.get("launch").size())
                .priceFinanceCount(byEvent.get("price_finance").size())
                .campaignCount(byEvent.get("campaign").size())
                .salesMilestoneCount(byEvent.get("sales_milestone").size())
                .benchmarkTableCount(matrices.size())
                .benchmarkModelCount(totalBenchmarkModels)
                .benchmarkParamCount(totalBenchmarkParams)
                .talkingPointsCount(talkingPoints.size())
                .filterAudit(filterAudit)
                .pushed(pushed)
                .build();
    }

    private Map<Long, CardInsight> buildCardInsights(Map<String, List<WebSearchNews>> byEvent,
                                                     List<WebSearchNews> marketHotEvents) {
        LinkedHashMap<Long, CardInsightInput> inputs = new LinkedHashMap<>();
        addCardInsightInputs(inputs, "launch", byEvent == null ? null : byEvent.get("launch"));
        addCardInsightInputs(inputs, "price_finance", byEvent == null ? null : byEvent.get("price_finance"));
        addCardInsightInputs(inputs, "campaign", byEvent == null ? null : byEvent.get("campaign"));
        addCardInsightInputs(inputs, "market_hot", marketHotEvents);
        if (inputs.isEmpty()) return Collections.emptyMap();
        try {
            return cardInsightAgent.generate(new ArrayList<>(inputs.values()));
        } catch (Exception e) {
            log.warn("[CompetitorPipeline] card insight LLM failed ignored: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, CardGroupSummary> buildCardGroupSummaries(Map<String, List<WebSearchNews>> byEvent,
                                                                  Map<Long, CardInsight> cardInsights) {
        List<GroupSummaryInput> inputs = cardBuilder.buildGroupSummaryInputs(byEvent, cardInsights);
        if (inputs.isEmpty()) return Collections.emptyMap();
        try {
            return cardGroupSummaryAgent.generate(inputs);
        } catch (Exception e) {
            log.warn("[CompetitorPipeline] card group summary LLM failed ignored: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static void addDroppedSamples(FilterAudit audit,
                                          List<WebSearchNews> before,
                                          List<WebSearchNews> after,
                                          String reason) {
        if (audit == null || before == null || before.isEmpty()) return;
        Set<Long> kept = after == null ? Collections.emptySet() : after.stream()
                .map(WebSearchNews::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int added = 0;
        for (WebSearchNews row : before) {
            if (row == null || row.getId() == null || kept.contains(row.getId())) continue;
            addAuditSample(audit, row, reason, null);
            if (++added >= 4 || audit.getSamples().size() >= 12) break;
        }
    }

    private static void enrichFilterAuditWithInsights(Map<String, FilterAudit> filterAudit,
                                                      Map<String, List<WebSearchNews>> byEvent,
                                                      Map<Long, CardInsight> insights) {
        if (filterAudit == null || filterAudit.isEmpty() || byEvent == null) return;
        for (Map.Entry<String, FilterAudit> entry : filterAudit.entrySet()) {
            String eventType = entry.getKey();
            FilterAudit audit = entry.getValue();
            List<WebSearchNews> rows = byEvent.getOrDefault(eventType, Collections.emptyList());
            int valuable = 0;
            int displayed = 0;
            for (WebSearchNews row : rows) {
                boolean pass = isInsightValuableForDisplay(insights, row);
                if (pass) {
                    valuable++;
                    displayed++;
                } else if ("campaign".equals(eventType)) {
                    displayed++;
                    addAuditSample(audit, row, "campaign_displayed_without_llm_value", insightHideReason(insights, row));
                } else if (!"launch".equals(eventType)) {
                    addAuditSample(audit, row, "llm_low_value_or_empty", insightHideReason(insights, row));
                } else {
                    displayed++;
                }
            }
            audit.setLlmValuableCount(valuable);
            audit.setDisplayedCount(displayed);
            log.info("[CompetitorPipeline] filter_audit {} raw={} brand={} deduped={} llm_valuable={} displayed={} samples={}",
                    eventType, audit.getRawCount(), audit.getBrandFilteredCount(), audit.getDedupedCount(),
                    audit.getLlmValuableCount(), audit.getDisplayedCount(), audit.getSamples().size());
        }
    }

    private static boolean isInsightValuableForDisplay(Map<Long, CardInsight> insights, WebSearchNews row) {
        if (row == null || row.getId() == null || insights == null) return false;
        CardInsight insight = insights.get(row.getId());
        if (insight == null || Boolean.FALSE.equals(insight.getValuable())) return false;
        if (insight.getValueScore() != null && insight.getValueScore() < 6.0d) return false;
        if (insight.getConfidence() != null && insight.getConfidence() < 0.35d) return false;
        boolean hasPoint = insight.getPoints() != null && !insight.getPoints().isEmpty();
        boolean hasDetail = insight.getDetailHighlight() != null && !insight.getDetailHighlight().trim().isEmpty();
        return hasPoint || hasDetail;
    }

    private static String insightHideReason(Map<Long, CardInsight> insights, WebSearchNews row) {
        if (row == null || row.getId() == null || insights == null) return "no_insight";
        CardInsight insight = insights.get(row.getId());
        if (insight == null) return "no_insight";
        if (insight.getHideReason() != null && !insight.getHideReason().trim().isEmpty()) {
            return insight.getHideReason().trim();
        }
        if (Boolean.FALSE.equals(insight.getValuable())) return "llm_marked_not_valuable";
        if (insight.getValueScore() != null && insight.getValueScore() < 6.0d) return "value_score_below_6";
        if (insight.getConfidence() != null && insight.getConfidence() < 0.35d) return "confidence_below_0_35";
        return "no_points_or_detail";
    }

    private static void addAuditSample(FilterAudit audit, WebSearchNews row, String reason, String detail) {
        if (audit == null || row == null || audit.getSamples() == null || audit.getSamples().size() >= 12) return;
        audit.getSamples().add(FilterSample.builder()
                .id(row.getId())
                .brand(row.getBrandName())
                .sourceTool(row.getSourceTool())
                .title(truncate(firstNonBlank(row.getEventSummary(), row.getTitle(), row.getContent()), 80))
                .reason(reason)
                .detail(detail)
                .build());
    }

    private static void addCardInsightInputs(Map<Long, CardInsightInput> out,
                                             String section,
                                             List<WebSearchNews> rows) {
        if (rows == null || rows.isEmpty()) return;
        for (WebSearchNews row : rows) {
            if (row == null || row.getId() == null) continue;
            out.putIfAbsent(row.getId(), new CardInsightInput(row, section));
        }
    }

    private static boolean hasStrongFinancePolicySignal(WebSearchNews row) {
        if (row == null) return false;
        String primaryText = String.join(" ",
                safeStr(row.getEventSummary()),
                safeStr(row.getTitle()),
                safeStr(row.getImageOcrText()))
                .replaceAll("\\s+", " ")
                .trim();
        String content = safeStr(row.getContent()).replaceAll("\\s+", " ").trim();
        String tool = safeStr(row.getSourceTool());
        boolean official = tool.endsWith("_official");
        String text = official ? (primaryText + " " + content).trim() : primaryText;
        if (text.isEmpty() && content.isEmpty()) return false;

        String policyRegex = ".*(购车权益|限时权益|用户权益|权益包|权益价|优惠|补贴|置换|增换购|复购|首付|0息|零息|免息|低息|金融|贷款|保险权益|订金|定金|膨胀|抵扣|抵\\d|减免|现金礼|购车礼|保养礼|质保权益|终身质保|充电权益).*";
        boolean primaryStrongPolicy = primaryText.matches(policyRegex);
        boolean strongPolicy = text.matches(policyRegex);
        if (!strongPolicy && !official) {
            strongPolicy = content.matches(".*(购车权益|限时权益|用户权益|权益包|优惠|补贴|置换|增换购|首付|0息|零息|免息|低息|金融|订金|定金|膨胀|抵扣|减免).{0,24}(\\d|元|万|%|期|年|至高|最高).*")
                    || content.matches(".*(\\d|元|万|%|期|年|至高|最高).{0,24}(购车权益|限时权益|用户权益|权益包|优惠|补贴|置换|首付|0息|零息|免息|低息|金融|订金|定金|抵扣|减免).*");
        }
        if (!official && !primaryStrongPolicy) return false;
        if (!strongPolicy) return false;
        String comparable = (text + " " + (strongPolicy ? content : "")).trim();
        boolean purePriceOnly = comparable.matches(".*(售价|指导价|预售价|起售价|万元).*")
                && !text.matches(".*(权益|优惠|补贴|置换|增换购|首付|0息|零息|免息|低息|金融|保险|订金|定金|膨胀|抵扣|减免|购车礼|保养|质保).*");
        return !purePriceOnly;
    }

    private static String combineFinanceText(WebSearchNews row) {
        return String.join(" ",
                safeStr(row.getEventSummary()),
                safeStr(row.getTitle()),
                safeStr(row.getContent()),
                safeStr(row.getImageOcrText()))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean hasStrategicActionSignal(WebSearchNews row) {
        if (row == null) return false;
        String text = String.join(" ",
                safeStr(row.getEventSummary()),
                safeStr(row.getTitle()),
                safeStr(row.getContent()),
                safeStr(row.getImageOcrText()),
                safeStr(row.getModelMentioned()),
                safeStr(row.getBrandName()))
                .replaceAll("\\s+", " ")
                .trim();
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty()) return false;
        if (isDealerOrLocalPromo(safeStr(row.getUrl()).toLowerCase(Locale.ROOT), compact)) return false;
        if (compact.matches(".*(店内|到店|现车|询底价|让利促销|车主价格|优惠0\\.00|暂无优惠).*")) return false;

        boolean productRoadmap = compact.matches(".*(产品路线|产品规划|产品矩阵|车型规划|品牌矩阵|首期\\d+款|\\d+款新车|未来\\d+年|2026年量产|2027年量产|中长期规划).*");
        boolean techRoadmap = compact.matches(".*(技术路线|平台架构|技术平台|电子电气架构|智能化战略|智驾路线|辅助驾驶方案).*")
                && compact.matches(".*(战略|路线|规划|矩阵|体系|架构|合作|自研|迭代).*");
        boolean supplyOrPartner = compact.matches(".*(供应链|合作伙伴|战略合作|深化合作|合作2\\.0|联合开发|联合设计|签约|合作协议|生态合作).*")
                || (compact.matches(".*(引望|华为|华为乾崑|宁德时代|比亚迪|博世|地平线|Momenta|大疆车载|采埃孚).*")
                && compact.matches(".*(合作|联合|供应|签约|战略|伙伴).*"));
        boolean capacity = compact.matches(".*(产能|工厂|基地|投产|扩产|量产|生产线|交付爬坡|产线|下线|年产|月产).*");
        boolean overseas = compact.matches(".*(出海|海外|出口|欧洲|中东|东南亚|泰国|印尼|马来西亚|澳洲|拉美|全球化|海外上市|海外交付|海外渠道).*");
        boolean orgCapital = compact.matches(".*(组织调整|架构调整|人事调整|高管|CEO|总裁|融资|增资|股权|投资|上市辅导|IPO|资本|并购|重组).*");
        boolean hasBrand = compact.matches(".*(猛士|仰望|坦克|方程豹|问界|AITO|路虎|卫士).*");
        boolean plainProductEvent = compact.matches(".*(上市|发布|预售|亮相|申报图|配置升级|售价|权益).*")
                && !(productRoadmap || techRoadmap || supplyOrPartner || capacity || overseas || orgCapital);
        return hasBrand && !plainProductEvent
                && (productRoadmap || techRoadmap || supplyOrPartner || capacity || overseas || orgCapital);
    }

    private static boolean hasMarketHotSignal(WebSearchNews row) {
        if (row == null) return false;
        String url = safeStr(row.getUrl()).toLowerCase(Locale.ROOT);
        String text = String.join(" ",
                safeStr(row.getEventSummary()),
                safeStr(row.getTitle()),
                safeStr(row.getContent()),
                safeStr(row.getModelMentioned()),
                safeStr(row.getBrandName()))
                .replaceAll("\\s+", " ")
                .trim();
        String compact = text.replaceAll("\\s+", "");
        if (compact.isEmpty()) return false;
        if (!isMarketHotSourceAllowed(url)) return false;
        if (isDealerOrLocalPromo(url, compact)) return false;
        if (isFixedBenchmarkMarketText(compact)) return false;
        if (compact.matches(".*(B端运营|营运市场|出租车|网约车|警用|公务用车).*")) return false;
        if (compact.matches(".*(亚马逊限量版|萧邦版|联名版).*") && !compact.matches(".*(订单|大定|销量|交付|破圈|声量).*")) return false;
        if (compact.matches(".*(充电桩|电桩|渗透率|营收|财报|利润|毛利|车企冠军|汽车行业|新能源行业).*")
                && !compact.matches(".*(车型|新车|订单|大定|销量|交付|投诉|召回|事故|争议).*")) {
            return false;
        }

        boolean strongMarketSignal = compact.matches(".*(大定|订单|锁单|小订|交付|销量|热销|爆款|破\\d|突破|冠军|榜首|价格战|官降|限时权益|购车权益|置换补贴|金融政策|0息|免息|智驾|NOA|激光雷达|800V|固态电池|电池平台|投诉|召回|事故|自燃|维权|争议|舆情|口碑|安全隐患|代言|破圈|出圈).*");
        boolean plainLaunchOnly = compact.matches(".*(上市|发布|预售|亮相).*")
                && !compact.matches(".*(大定|订单|销量|交付|权益|补贴|置换|0息|免息|智驾|激光雷达|800V|电池|投诉|召回|事故|争议|破圈|用户|家庭|越野).*");
        return strongMarketSignal && !plainLaunchOnly;
    }

    private static boolean isMarketHotSourceAllowed(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.contains("autohome.com.cn")
                || url.contains("dongchedi.com")
                || url.contains("yiche.com")
                || url.contains("pcauto.com.cn")
                || url.contains("gasgoo.com")
                || url.contains("auto.sina.com.cn")
                || url.contains("auto.sohu.com")
                || url.contains("k.sina.com.cn");
    }

    private static boolean isDealerOrLocalPromo(String url, String compactText) {
        String u = url == null ? "" : url;
        if (u.matches(".*(/cheshi/|/dealer/|/4s/|/price/|/jiangjia/|/youhui/).*")) return true;
        return compactText.matches(".*(让利促销|店内|到店|现车|本店|购车热线|车主价格|优惠0\\.00|暂无优惠|降价促销|少量现车|询底价).*");
    }

    private static boolean isFixedBenchmarkMarketText(String compactText) {
        if (compactText == null) return false;
        return compactText.matches(".*(猛士|仰望|坦克|方程豹|问界|AITO|路虎|卫士).*");
    }

    /** 仅保留 brand_name 在对标白名单内的事件（去掉 brand=行业/阿维塔/特斯拉/理想/智界 等）。
     *  v7: sales_milestone 板块额外放行「乘联会」「中汽协」两个行业权威源。 */
    private List<WebSearchNews> filterByBenchmarkBrands(List<WebSearchNews> rows, String eventType) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        boolean isSales = "sales_milestone".equals(eventType);
        List<WebSearchNews> out = new ArrayList<>();
        for (WebSearchNews n : rows) {
            String brand = n.getBrandName();
            if (brand == null) continue;
            if (BRAND_ORDER_INDEX.containsKey(brand)) {
                out.add(n);
            } else if (isSales && SALES_AUTHORITY_BRANDS.contains(brand)) {
                out.add(n);
            }
        }
        return out;
    }

    /** 同品牌聚集 + 品牌按对标顺序 + 同品牌内按 importance DESC + add_ts DESC。
     *  v7: 行业权威 brand（乘联会/中汽协）排在对标品牌之后、未知品牌之前。 */
    private List<WebSearchNews> sortByBrandThenImportance(List<WebSearchNews> rows) {
        List<WebSearchNews> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            int idxA = brandOrderIndex(a.getBrandName());
            int idxB = brandOrderIndex(b.getBrandName());
            if (idxA != idxB) return Integer.compare(idxA, idxB);
            int impA = a.getEventImportance() == null ? 0 : a.getEventImportance();
            int impB = b.getEventImportance() == null ? 0 : b.getEventImportance();
            if (impA != impB) return Integer.compare(impB, impA);
            long tsA = a.getAddTs() == null ? 0 : a.getAddTs();
            long tsB = b.getAddTs() == null ? 0 : b.getAddTs();
            return Long.compare(tsB, tsA);
        });
        return sorted;
    }

    /** 去掉品牌前缀方便模糊匹配（如「方程豹豹5」→「豹5」、「猛士M817」→「M817」）。 */
    private static String stripBrandPrefix(String model) {
        if (model == null) return "";
        String[] prefixes = {"方程豹", "猛士汽车", "猛士", "仰望", "坦克", "问界", "路虎", "卫士"};
        for (String p : prefixes) {
            if (model.startsWith(p) && model.length() > p.length()) {
                return model.substring(p.length());
            }
        }
        return model;
    }

    private static int brandOrderIndex(String brand) {
        if (brand == null) return 99;
        Integer idx = BRAND_ORDER_INDEX.get(brand);
        if (idx != null) return idx;
        if (SALES_AUTHORITY_BRANDS.contains(brand)) return BRAND_ORDER_INDEX.size();
        return 99;
    }

    /**
     * 双层去重：
     *  L1: 完全相同 url_hash（不可能重复进库但保险）
     *  L2: 同 (brand, first_model, event_type) — 同品牌同车型同类型视为一事件，保留 importance 最高
     *  L3: 没车型时回退按 event_summary 前 12 字 + brand
     *  已按 importance DESC, add_ts DESC 排序，先到的保留。
     */
    private List<WebSearchNews> dedupBySummary(List<WebSearchNews> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        LinkedHashMap<String, WebSearchNews> kept = new LinkedHashMap<>();
        for (WebSearchNews n : rows) {
            String brand = n.getBrandName() == null ? "" : n.getBrandName();
            String firstModel = firstModel(n.getModelMentioned());
            String key;
            if (!firstModel.isEmpty()) {
                // L2: brand + model + event_type
                key = "M|" + brand + "|" + firstModel + "|" + safeStr(n.getEventType());
            } else {
                // L3 兜底：brand + summary 前 12 字
                String summary = n.getEventSummary();
                if (summary == null || summary.isEmpty()) summary = n.getTitle();
                if (summary == null) summary = "";
                String prefix = summary.length() > 12 ? summary.substring(0, 12) : summary;
                key = "S|" + brand + "|" + prefix;
            }
            kept.putIfAbsent(key, n);
        }
        return new ArrayList<>(kept.values());
    }

    /** model_mentioned 形如「问界M9,问界M9系列」— 取第一个且规范化（去空格、去「系列/版」后缀） */
    private static String firstModel(String models) {
        if (models == null || models.isEmpty()) return "";
        String first = models.split(",")[0].trim();
        // 去常见后缀让「问界M9」「问界M9系列」合并
        first = first.replaceAll("(系列|版|款|车型)$", "");
        return first;
    }

    private static String safeStr(String s) { return s == null ? "" : s; }

    /**
     * 模糊去重：同 brand 内（同/不同 event_type 都行），字符 bigram Jaccard 相似度 ≥ 阈值视为同事件。
     * 保留先来的（已按 importance DESC 排），后来的丢弃。
     * 例：「坦克车队参加2026环塔拉力赛」/「长城汽车出征2026环塔国际拉力赛」会被合并。
     */
    private List<WebSearchNews> dedupBySimilarity(List<WebSearchNews> rows) {
        if (rows == null || rows.size() < 2) return rows == null ? Collections.emptyList() : rows;
        List<WebSearchNews> kept = new ArrayList<>();
        List<Set<String>> keptBigrams = new ArrayList<>();
        for (WebSearchNews n : rows) {
            String summary = n.getEventSummary();
            if (summary == null || summary.isEmpty()) summary = n.getTitle();
            Set<String> bg = extractBigrams(summary == null ? "" : summary);
            boolean dup = false;
            for (int i = 0; i < kept.size(); i++) {
                // 必须同品牌才有可能是同事件
                if (!java.util.Objects.equals(kept.get(i).getBrandName(), n.getBrandName())) continue;
                if (jaccard(bg, keptBigrams.get(i)) >= DEDUP_JACCARD_THRESHOLD) {
                    dup = true;
                    log.debug("[CompetitorPipeline] dedup-sim: '{}' ~ '{}'",
                            truncate(summary, 30),
                            truncate(kept.get(i).getEventSummary(), 30));
                    break;
                }
            }
            if (!dup) {
                kept.add(n);
                keptBigrams.add(bg);
            }
        }
        return kept;
    }

    private static Set<String> extractBigrams(String s) {
        if (s == null || s.isEmpty()) return Collections.emptySet();
        // 去数字、英文标点、空白
        String clean = s.replaceAll("[\\d\\p{Punct}\\s\\u3000\\uFF00-\\uFFEF]", "");
        Set<String> bigrams = new HashSet<>();
        for (int i = 0; i + 2 <= clean.length(); i++) {
            String bg = clean.substring(i, i + 2);
            if (!STOP_BIGRAMS.contains(bg)) bigrams.add(bg);
        }
        return bigrams;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int intersectSize = 0;
        for (String x : a) if (b.contains(x)) intersectSize++;
        int unionSize = a.size() + b.size() - intersectSize;
        return unionSize == 0 ? 0.0 : (double) intersectSize / unionSize;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ===== Benchmark Matrix =====

    public static class BenchmarkMatrix {
        /** 本品车型名（仅在 v3 多桌模式有值）— 如「猛士M817」 */
        public String selfModel;
        /** 顺序：self 在前，competitor 在后 */
        public final List<String> modelNames = new ArrayList<>();
        /** 每行：参数名 + 各 modelName -> value */
        public final List<ParamRow> paramRows = new ArrayList<>();
    }

    public static class ParamRow {
        public final String category;
        public final String name;
        /** modelName -> value */
        public final Map<String, String> values = new LinkedHashMap<>();

        public ParamRow(String category, String name) {
            this.category = category;
            this.name = name;
        }
    }

    /** v3 多桌对标：按 vs_self_model 分组，每个本品车型一张矩阵。 */
    private List<BenchmarkMatrix> buildBenchmarkMatrices() {
        List<AutohomeSeriesConfig> all = seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .isNotNull(AutohomeSeriesConfig::getVsSelfModel)
                        .orderByAsc(AutohomeSeriesConfig::getVsSelfModel)
                        .orderByAsc(AutohomeSeriesConfig::getId));
        if (all.isEmpty()) return Collections.emptyList();

        // 也把本品车型本身查出来（vs_self_model=NULL 但 role=self 那行）
        List<AutohomeSeriesConfig> selfRows = seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .eq(AutohomeSeriesConfig::getRole, "self"));
        Map<String, AutohomeSeriesConfig> selfByName = selfRows.stream()
                .collect(Collectors.toMap(AutohomeSeriesConfig::getModelName, c -> c, (a, b) -> a));

        // 按 vs_self_model 分组
        LinkedHashMap<String, List<AutohomeSeriesConfig>> grouped = all.stream()
                .collect(Collectors.groupingBy(
                        AutohomeSeriesConfig::getVsSelfModel,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<BenchmarkMatrix> out = new ArrayList<>();
        for (Map.Entry<String, List<AutohomeSeriesConfig>> e : grouped.entrySet()) {
            String selfModel = e.getKey();
            List<AutohomeSeriesConfig> competitors = e.getValue();
            BenchmarkMatrix m = new BenchmarkMatrix();
            m.selfModel = selfModel;

            // 我方车型排第一列（如果有抓到参数）
            AutohomeSeriesConfig selfCfg = selfByName.get(selfModel);
            List<AutohomeSeriesConfig> all4 = new ArrayList<>();
            if (selfCfg != null) all4.add(selfCfg);
            all4.addAll(competitors);

            populateMatrix(m, all4);
            out.add(m);
        }
        return out;
    }

    /** 给一张矩阵填充参数数据（共享逻辑）。 */
    private void populateMatrix(BenchmarkMatrix m, List<AutohomeSeriesConfig> cfgs) {
        LinkedHashMap<String, LinkedHashMap<String, Map<String, String>>> grouped = new LinkedHashMap<>();
        for (AutohomeSeriesConfig cfg : cfgs) {
            List<AutohomeSpec> specs = specMapper.findLatestBySeriesId(cfg.getSeriesId());
            if (specs.isEmpty()) {
                // 即使没数据也加入列名，前端能看出"这款待抓"
                m.modelNames.add(cfg.getModelName());
                continue;
            }
            String repSpecId = specs.stream().map(AutohomeSpec::getSpecId)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            if (repSpecId == null) {
                m.modelNames.add(cfg.getModelName());
                continue;
            }
            List<AutohomeSpec> rep = specs.stream()
                    .filter(s -> repSpecId.equals(s.getSpecId()))
                    .filter(s -> isKeyParam(s.getParamName()))
                    .collect(Collectors.toList());
            m.modelNames.add(cfg.getModelName());
            for (AutohomeSpec s : rep) {
                String cat = safe(s.getParamCategory());
                String pname = safe(s.getParamName());
                String val = safe(s.getParamValue());
                grouped.computeIfAbsent(cat, k -> new LinkedHashMap<>())
                       .computeIfAbsent(pname, k -> new LinkedHashMap<>())
                       .put(cfg.getModelName(), val);
            }
        }
        for (Map.Entry<String, LinkedHashMap<String, Map<String, String>>> catEntry : grouped.entrySet()) {
            for (Map.Entry<String, Map<String, String>> pEntry : catEntry.getValue().entrySet()) {
                ParamRow row = new ParamRow(catEntry.getKey(), pEntry.getKey());
                row.values.putAll(pEntry.getValue());
                m.paramRows.add(row);
            }
        }
    }

    private boolean isKeyParam(String name) {
        if (name == null) return false;
        for (String key : KEY_PARAM_NAMES) {
            if (name.contains(key)) return true;
        }
        return false;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    @Data
    @Builder
    public static class FilterAudit {
        private int rawCount;
        private int brandFilteredCount;
        private int dedupedCount;
        private int llmValuableCount;
        private int displayedCount;
        private List<FilterSample> samples;
    }

    @Data
    @Builder
    public static class FilterSample {
        private Long id;
        private String brand;
        private String sourceTool;
        private String title;
        private String reason;
        private String detail;
    }

    @Data
    @Builder
    public static class Result {
        private boolean success;
        private String error;
        private String cardJson;
        private String briefing;
        private int launchCount;
        private int priceFinanceCount;
        private int campaignCount;
        private int salesMilestoneCount;
        private int benchmarkTableCount;
        private int benchmarkModelCount;
        private int benchmarkParamCount;
        private int talkingPointsCount;
        private Map<String, FilterAudit> filterAudit;
        private boolean pushed;
    }
}
