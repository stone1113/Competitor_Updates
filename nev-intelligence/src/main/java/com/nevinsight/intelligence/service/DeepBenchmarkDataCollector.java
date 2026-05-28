package com.nevinsight.intelligence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.model.util.AutohomeParamNormalizer;
import com.nevinsight.intelligence.dto.BenchmarkContext;
import com.nevinsight.intelligence.dto.BenchmarkContext.ModelData;
import com.nevinsight.intelligence.dto.BenchmarkContext.SalesSnapshot;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.AutohomeSpec;
import com.nevinsight.model.entity.core.GasgooSalesRecord;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.AutohomeSpecMapper;
import com.nevinsight.model.mapper.core.GasgooSalesRecordMapper;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * v9 深度对标：第一步「数据采集」（非 LLM）。
 * 拉本品 + N 个竞品的：
 *   - autohome_spec 67 参数（规范化 + 过滤空值）
 *   - gasgoo_sales_record 近 3 月销量
 *   - web_search_news 32h 内官号价格金融事件
 *
 * 输出 BenchmarkContext，喂给 5 个 Analyst Agent。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepBenchmarkDataCollector {

    private static final long PRICE_WINDOW_MS = 32L * 3600 * 1000;

    private final AutohomeSeriesConfigMapper configMapper;
    private final AutohomeSpecMapper specMapper;
    private final GasgooSalesRecordMapper gasgooMapper;
    private final WebSearchNewsMapper newsMapper;

    /**
     * 收集本品 + N 个竞品的完整数据。
     * @param selfModel 本品车型名（必须存在于 autohome_series_config 且 role=self）
     * @param competitorModels 竞品车型名列表
     */
    public BenchmarkContext collect(String selfModel, List<String> competitorModels) {
        long start = System.currentTimeMillis();
        BenchmarkContext ctx = new BenchmarkContext();
        ctx.setSelfModel(selfModel);

        List<String> all = new ArrayList<>();
        all.add(selfModel);
        if (competitorModels != null) {
            for (String m : competitorModels) {
                if (m != null && !m.trim().isEmpty() && !all.contains(m.trim())) {
                    all.add(m.trim());
                }
            }
        }
        ctx.setAllModels(all);

        for (String modelName : all) {
            ModelData md = collectOne(modelName);
            ctx.getModelDataMap().put(modelName, md);
            // 推断本品级别（首个 self 的「级别」参数）
            if (modelName.equals(selfModel) && ctx.getSelfClass() == null) {
                String level = md.getSpecs().get("级别");
                if (level != null) ctx.setSelfClass(level);
            }
        }

        ctx.setCollectedTs(System.currentTimeMillis());
        log.info("[DeepBenchmark] DataCollector 完成 self={} competitors={} 耗时={}ms",
                selfModel, competitorModels, ctx.getCollectedTs() - start);
        return ctx;
    }

    private ModelData collectOne(String modelName) {
        ModelData md = new ModelData();
        md.setModelName(modelName);

        // 1. 车系配置
        AutohomeSeriesConfig cfg = configMapper.selectOne(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getModelName, modelName)
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .last("LIMIT 1"));
        if (cfg == null) {
            log.warn("[DeepBenchmark] 车系配置缺失: {}", modelName);
            return md;
        }
        md.setBrandName(cfg.getBrandName());
        md.setRole(cfg.getRole());
        md.setAutohomeSeriesId(cfg.getSeriesId());
        md.setGasgooSeriesId(cfg.getGasgooSeriesId());

        // 2. autohome 参数：取最新 spec_id 字典序最大者的全部参数（规范化 + 过滤空值）
        List<AutohomeSpec> specs = specMapper.findLatestBySeriesId(cfg.getSeriesId());
        if (!specs.isEmpty()) {
            String repSpecId = specs.stream().map(AutohomeSpec::getSpecId)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            if (repSpecId != null) {
                for (AutohomeSpec s : specs) {
                    if (!repSpecId.equals(s.getSpecId())) continue;
                    String catName = s.getParamCategory() == null ? "" : s.getParamCategory();
                    String paramName = AutohomeParamNormalizer.normalize(catName, s.getParamName());
                    String val = s.getParamValue();
                    if (paramName == null || paramName.isEmpty()) continue;
                    if (AutohomeParamNormalizer.isBlankValue(val)) continue;
                    md.getSpecs().put(paramName, val);
                }
            }
        }

        // 3. 盖世近 3 月销量
        if (cfg.getGasgooSeriesId() != null && !cfg.getGasgooSeriesId().isEmpty()) {
            List<GasgooSalesRecord> sales = gasgooMapper.selectList(
                    new LambdaQueryWrapper<GasgooSalesRecord>()
                            .eq(GasgooSalesRecord::getGasgooSeriesId, cfg.getGasgooSeriesId())
                            .orderByDesc(GasgooSalesRecord::getPeriodYear)
                            .orderByDesc(GasgooSalesRecord::getPeriodMonth)
                            .last("LIMIT 3"));
            md.setRecentSales(sales.stream().map(g -> {
                SalesSnapshot s = new SalesSnapshot();
                s.setYear(g.getPeriodYear());
                s.setMonth(g.getPeriodMonth());
                s.setSalesCount(g.getSalesCount());
                s.setYtdCount(g.getYtdCount());
                return s;
            }).collect(Collectors.toList()));
        }

        // 4. 32h 内官号金融政策事件（用车型名 / 品牌名模糊匹配）
        long sinceMs = System.currentTimeMillis() - PRICE_WINDOW_MS;
        // 复用 findLatestFinanceByModel（已含车型名模糊 + 官号过滤）
        String modelKey = stripBrandPrefix(modelName);
        List<WebSearchNews> priceEvents = newsMapper.findLatestFinanceByModel(modelKey, 5);
        md.setPriceEvents(priceEvents.stream()
                .filter(n -> n.getAddTs() != null && n.getAddTs() >= sinceMs)
                .map(n -> n.getEventSummary() != null ? n.getEventSummary() : n.getTitle())
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList()));

        return md;
    }

    /** 去品牌前缀做模糊匹配（与 FinanceByModelController 同逻辑） */
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
}
