package com.nevinsight.collector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.collector.client.GasgooSalesScraper;
import com.nevinsight.collector.client.GasgooSalesScraper.ScrapeResult;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.GasgooSalesRecord;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.GasgooSalesRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 盖世汽车销量同步：扫所有 enabled + 已配置 gasgoo_series_id 的车系，
 * 抓近 lookbackMonths 个月的销量，upsert 到 gasgoo_sales_record。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GasgooSalesSyncService {

    /** 默认抓近 3 个月（盖世数据通常上月底-当月初出齐） */
    private static final int DEFAULT_LOOKBACK_MONTHS = 3;

    /** 同一 series 多月之间的礼貌间隔 */
    private static final long INTER_REQUEST_MS = 1500L;

    private final AutohomeSeriesConfigMapper seriesConfigMapper;
    private final GasgooSalesRecordMapper salesMapper;
    private final GasgooSalesScraper scraper;

    /** 全量同步：所有 enabled + gasgoo_series_id 非空的车系。 */
    public Result syncAll(int lookbackMonths) {
        if (lookbackMonths <= 0) lookbackMonths = DEFAULT_LOOKBACK_MONTHS;
        List<AutohomeSeriesConfig> targets = seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .isNotNull(AutohomeSeriesConfig::getGasgooSeriesId)
                        .ne(AutohomeSeriesConfig::getGasgooSeriesId, ""));
        log.info("[GasgooSync] targets={} lookback={}m", targets.size(), lookbackMonths);

        int totalSeries = targets.size();
        int totalRows = 0;
        int totalFail = 0;
        List<String> errors = new ArrayList<>();
        for (AutohomeSeriesConfig cfg : targets) {
            try {
                int n = syncOne(cfg, lookbackMonths);
                totalRows += n;
            } catch (Exception e) {
                totalFail++;
                String err = String.format("[%s/%s gasgoo=%s] %s",
                        cfg.getBrandName(), cfg.getModelName(), cfg.getGasgooSeriesId(), e.getMessage());
                log.warn("[GasgooSync] sync one failed: {}", err);
                errors.add(err);
            }
        }
        log.info("[GasgooSync] done series={} rows={} fail={}", totalSeries, totalRows, totalFail);
        return new Result(totalSeries, totalRows, totalFail, errors);
    }

    /** 单车系同步指定回看月数。返回写入 / upsert 的行数。
     *  注意：盖世销量数据滞后一月（如 5 月只看得到 4 月销量），所以从「上个月」起算回看 lookbackMonths 个月。 */
    public int syncOne(AutohomeSeriesConfig cfg, int lookbackMonths) {
        // 销量滞后一月：起点 = 上个月
        LocalDate startMonth = LocalDate.now().minusMonths(1);
        int written = 0;
        for (int i = 0; i < lookbackMonths; i++) {
            LocalDate target = startMonth.minusMonths(i);
            int year = target.getYear();
            int month = target.getMonthValue();
            ScrapeResult sr = scraper.scrape(cfg.getGasgooSeriesId(), year, month);
            if (sr == null || !sr.isValid()) {
                log.debug("[GasgooSync] {}/{} {}-{} no data, skip",
                        cfg.getBrandName(), cfg.getModelName(), year, month);
                sleep(INTER_REQUEST_MS);
                continue;
            }
            GasgooSalesRecord rec = new GasgooSalesRecord();
            rec.setBrandName(cfg.getBrandName());
            rec.setModelName(cfg.getModelName());
            rec.setGasgooSeriesId(cfg.getGasgooSeriesId());
            rec.setPeriodYear(year);
            rec.setPeriodMonth(month);
            rec.setSalesCount(sr.salesCount);
            rec.setBrandTotal(sr.brandTotal);
            rec.setSegmentTotal(sr.segmentTotal);
            rec.setYtdCount(sr.ytdCount);
            rec.setSourceUrl(sr.sourceUrl);
            rec.setCrawlTs(System.currentTimeMillis());
            salesMapper.upsert(rec);
            written++;
            log.info("[GasgooSync] ✓ {}/{} {}-{}: sales={} brand_total={} segment_total={}",
                    cfg.getBrandName(), cfg.getModelName(), year, month,
                    sr.salesCount, sr.brandTotal, sr.segmentTotal);
            sleep(INTER_REQUEST_MS);
        }
        return written;
    }

    /** 通过 config id 同步单条 — 用于 UI「立即抓取」按钮。 */
    public int syncByConfigId(Long configId, int lookbackMonths) {
        AutohomeSeriesConfig cfg = seriesConfigMapper.selectById(configId);
        if (cfg == null) throw new IllegalArgumentException("config not found: " + configId);
        if (cfg.getGasgooSeriesId() == null || cfg.getGasgooSeriesId().isEmpty()) {
            throw new IllegalArgumentException("gasgoo_series_id not configured for: " + cfg.getModelName());
        }
        if (lookbackMonths <= 0) lookbackMonths = DEFAULT_LOOKBACK_MONTHS;
        return syncOne(cfg, lookbackMonths);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static class Result {
        public final int seriesCount;
        public final int rowsWritten;
        public final int failCount;
        public final List<String> errors;
        public Result(int seriesCount, int rowsWritten, int failCount, List<String> errors) {
            this.seriesCount = seriesCount;
            this.rowsWritten = rowsWritten;
            this.failCount = failCount;
            this.errors = errors;
        }
    }
}
