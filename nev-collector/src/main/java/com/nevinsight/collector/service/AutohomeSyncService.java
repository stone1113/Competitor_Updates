package com.nevinsight.collector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.collector.client.CrawlerServiceClient;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.AutohomeSpec;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.AutohomeSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 每周一次从汽车之家抓取启用车系的参数，落库 autohome_spec。
 *
 * PR2 阶段只负责「抓 + 写 DB」；PR3 再加 RAGFlow 上传。
 *
 * 调度由 @Scheduled 在 nev-admin 侧触发（避免 nev-collector 启用 Spring Scheduling 影响其他模块）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutohomeSyncService {

    private final AutohomeSeriesConfigMapper seriesConfigMapper;
    private final AutohomeSpecMapper specMapper;
    private final CrawlerServiceClient crawlerClient;

    /** 单车系手动同步，前端「立刻抓取」用。 */
    public SyncResult syncOne(String seriesId) {
        AutohomeSeriesConfig cfg = seriesConfigMapper.selectOne(
                new LambdaQueryWrapper<AutohomeSeriesConfig>().eq(AutohomeSeriesConfig::getSeriesId, seriesId));
        if (cfg == null) {
            return new SyncResult(0, 0, List.of(seriesId));
        }
        return runOn(List.of(cfg));
    }

    /** 返回写入的参数行数（每个车系 N 个参数）。 */
    public SyncResult syncAll() {
        List<AutohomeSeriesConfig> configs = seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true));
        if (configs.isEmpty()) {
            log.warn("[Autohome] no enabled series configs; nothing to sync");
            return new SyncResult(0, 0, Collections.emptyList());
        }
        return runOn(configs);
    }

    private SyncResult runOn(List<AutohomeSeriesConfig> configs) {
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();

        List<String> seriesIds = configs.stream()
                .map(AutohomeSeriesConfig::getSeriesId)
                .collect(Collectors.toList());

        log.info("[Autohome] sync starting for {} series: {}", seriesIds.size(), seriesIds);
        List<Map<String, Object>> items = crawlerClient.scrapeAutohome(seriesIds);

        int totalRows = 0;
        int successSeries = 0;
        List<String> failedSeries = new ArrayList<>();
        // crawler-service 整体不可用时返空 list — 把全部车系算 failed
        if (items.isEmpty()) {
            log.warn("[Autohome] crawler-service returned no items; marking all {} series failed",
                    seriesIds.size());
            failedSeries.addAll(seriesIds);
            return new SyncResult(0, 0, failedSeries);
        }
        // 收集 crawler-service 漏返的 series
        Set<String> returnedIds = items.stream()
                .map(i -> String.valueOf(i.get("series_id")))
                .collect(java.util.stream.Collectors.toSet());
        for (String sid : seriesIds) {
            if (!returnedIds.contains(sid)) failedSeries.add(sid);
        }

        for (Map<String, Object> item : items) {
            String sid = String.valueOf(item.get("series_id"));
            Boolean success = (Boolean) item.get("success");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> specs = (List<Map<String, Object>>) item.get("specs");

            if (!Boolean.TRUE.equals(success) || specs == null || specs.isEmpty()) {
                log.warn("[Autohome] series={} failed/empty error={}", sid, item.get("error"));
                failedSeries.add(sid);
                continue;
            }

            for (Map<String, Object> s : specs) {
                AutohomeSpec spec = new AutohomeSpec();
                spec.setSeriesId(sid);
                spec.setSpecId(str(s.get("spec_id")));
                spec.setSpecName(str(s.get("spec_name")));
                spec.setParamCategory(str(s.get("param_category")));
                spec.setParamName(str(s.get("param_name")));
                spec.setParamValue(truncate(str(s.get("param_value")), 500));
                spec.setCrawlDate(today);
                spec.setAddTs(now);
                if (spec.getSpecId() == null || spec.getSpecId().isEmpty()
                        || spec.getParamName() == null || spec.getParamName().isEmpty()) {
                    continue;
                }
                try {
                    specMapper.upsert(spec);
                    totalRows++;
                } catch (Exception e) {
                    log.warn("[Autohome] upsert failed sid={} spec_id={} param={}: {}",
                            sid, spec.getSpecId(), spec.getParamName(), e.getMessage());
                }
            }
            successSeries++;
        }

        log.info("[Autohome] sync done: {} series ok / {} failed, total {} rows",
                successSeries, failedSeries.size(), totalRows);
        return new SyncResult(successSeries, totalRows, failedSeries);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    public static class SyncResult {
        public final int successSeriesCount;
        public final int totalRows;
        public final List<String> failedSeriesIds;

        public SyncResult(int successSeriesCount, int totalRows, List<String> failedSeriesIds) {
            this.successSeriesCount = successSeriesCount;
            this.totalRows = totalRows;
            this.failedSeriesIds = failedSeriesIds;
        }
    }
}
