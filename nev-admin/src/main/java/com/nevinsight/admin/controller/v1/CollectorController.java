package com.nevinsight.admin.controller.v1;

import com.nevinsight.collector.service.WebNewsCollectorService;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.intelligence.config.BrandConfigProperties;
import com.nevinsight.intelligence.service.NewsEventExtractionService;
import com.nevinsight.intelligence.service.OcrEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final WebNewsCollectorService webNewsCollector;
    private final BrandConfigProperties brandConfig;
    private final NewsEventExtractionService eventService;
    private final OcrEnrichmentService ocrService;

    /**
     * 触发搜索引擎新闻采集
     * @param category all（默认）| self | competitor | industry | brand(legacy=self+competitor) | benchmark-model(按对标车型补采) | market-hot(市场热度补采)
     */
    @PostMapping("/collect-news")
    public ApiResponse<Map<String, Object>> collectNews(
            @RequestParam(defaultValue = "all") String category) {
        log.info("[Collector] 手动触发新闻采集 category={}", category);
        long start = System.currentTimeMillis();
        int inserted;
        if ("benchmark-model".equalsIgnoreCase(category)) {
            inserted = webNewsCollector.collectBenchmarkModelEvents();
        } else if ("market-hot".equalsIgnoreCase(category)) {
            inserted = webNewsCollector.collectMarketHotEvents();
        } else if ("all".equalsIgnoreCase(category)) {
            inserted = webNewsCollector.collectByCategory(null);
        } else {
            inserted = webNewsCollector.collectByCategory(category);
        }
        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> result = new HashMap<>();
        result.put("inserted", inserted);
        result.put("elapsedMs", elapsed);
        result.put("category", category);
        log.info("[Collector] 采集完成 category={} 新增 {} 条, 耗时 {}ms", category, inserted, elapsed);
        return ApiResponse.success(result);
    }

    /** 手动触发事件分类（处理 web_search_news.event_type IS NULL 的行）。 */
    @PostMapping("/extract-events")
    public ApiResponse<Map<String, Object>> extractEvents(
            @RequestParam(defaultValue = "25") int maxBatches) {
        log.info("[Collector] 手动触发事件分类 maxBatches={}", maxBatches);
        long start = System.currentTimeMillis();
        NewsEventExtractionService.Result r = eventService.extractPending(maxBatches);
        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> out = new HashMap<>();
        out.put("processed", r.processed);
        out.put("success", r.success);
        out.put("batchesRun", r.batchesRun);
        out.put("elapsedMs", elapsed);
        log.info("[Collector] 事件分类完成 processed={} success={} 耗时 {}ms",
                r.processed, r.success, elapsed);
        return ApiResponse.success(out);
    }

    /** v8: 手动触发 OCR 增强（扫官号 price_finance 候选有图未跑过 OCR 的条目）。 */
    @PostMapping("/ocr-enrich")
    public ApiResponse<Map<String, Object>> ocrEnrich(
            @RequestParam(defaultValue = "20") int limit) {
        log.info("[Collector] 手动触发 OCR 增强 limit={}", limit);
        long start = System.currentTimeMillis();
        OcrEnrichmentService.Result r = ocrService.run(limit);
        long elapsed = System.currentTimeMillis() - start;
        Map<String, Object> out = new HashMap<>();
        out.put("processed", r.processed);
        out.put("policyHit", r.policyHit);
        out.put("failed", r.failed);
        out.put("elapsedMs", elapsed);
        log.info("[Collector] OCR 增强完成 processed={} policy_hit={} failed={} 耗时 {}ms",
                r.processed, r.policyHit, r.failed, elapsed);
        return ApiResponse.success(out);
    }
}
