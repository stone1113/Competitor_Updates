package com.nevinsight.intelligence.service;

import com.nevinsight.intelligence.agent.EventExtractionAgent;
import com.nevinsight.intelligence.agent.EventExtractionAgent.EventResult;
import com.nevinsight.intelligence.agent.EventExtractionAgent.NewsLite;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 事件分类编排：读 web_search_news.event_type IS NULL → 批量调 EventExtractionAgent → 写回 5 字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsEventExtractionService {

    /** 每次 LLM 调用包多少条新闻（平衡 token 用量 / 失败爆炸半径）。 */
    private static final int BATCH_SIZE = 8;

    /** 单次 extractPending 默认最多处理多少条（避免单次跑太久）。 */
    public static final int DEFAULT_MAX_BATCHES = 25;  // 25 × 8 = 200 条

    private final WebSearchNewsMapper newsMapper;
    private final EventExtractionAgent agent;
    private final OcrEnrichmentService ocrEnrichmentService;

    /** 处理未分类的（最多 maxBatches × BATCH_SIZE 条）。返回 Result(processed, success_count, batches_run). */
    public Result extractPending(int maxBatches) {
        if (maxBatches <= 0) maxBatches = DEFAULT_MAX_BATCHES;
        int limit = maxBatches * BATCH_SIZE;
        List<WebSearchNews> pending = newsMapper.findPendingExtraction(limit);
        if (pending.isEmpty()) {
            log.info("[EventExtraction] no pending news");
            return new Result(0, 0, 0);
        }
        log.info("[EventExtraction] start pending={} maxBatches={}", pending.size(), maxBatches);

        int processed = 0;
        int success = 0;
        int batchesRun = 0;
        long now = System.currentTimeMillis();

        for (int i = 0; i < pending.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, pending.size());
            List<WebSearchNews> chunk = pending.subList(i, end);

            List<NewsLite> input = new ArrayList<>(chunk.size());
            for (WebSearchNews n : chunk) {
                input.add(new NewsLite(n.getId(), n.getTitle(), n.getContent(),
                        n.getSourceTool(), n.getBrandName()));
            }

            List<EventResult> results = Collections.emptyList();
            try {
                results = agent.classifyBatch(input);
            } catch (Exception e) {
                log.warn("[EventExtraction] batch {} failed: {}", batchesRun, e.getMessage());
            }
            batchesRun++;
            processed += chunk.size();

            // 建 id → brand_name 映射做 brand 错位 post-check
            java.util.Map<Long, String> idToBrand = new java.util.HashMap<>();
            for (WebSearchNews n : chunk) idToBrand.put(n.getId(), n.getBrandName());

            for (EventResult r : results) {
                String eventType = r.eventType;
                Integer importance = r.importance;
                String summary = r.eventSummary == null ? "" : r.eventSummary;
                String brand = idToBrand.getOrDefault(r.id, "");
                String models = r.models == null ? "" : r.models;

                // Brand 错位硬规则：brand 非空 + 非「行业」 + summary 和 models 都不含 brand 字面 → 强制 spam
                if (!brand.isEmpty() && !"行业".equals(brand) && !"市场热度".equals(brand)
                        && !summary.contains(brand) && !models.contains(brand)
                        && !"spam".equals(eventType) && !"other".equals(eventType)) {
                    log.info("[EventExtraction] brand 错位 → spam: id={} brand={} summary={}",
                            r.id, brand, summary.length() > 50 ? summary.substring(0, 50) : summary);
                    eventType = "spam";
                    importance = 0;
                }

                try {
                    int n = newsMapper.updateEventFields(
                            r.id, eventType, importance, summary, models, now);
                    if (n > 0) success++;
                } catch (Exception e) {
                    log.warn("[EventExtraction] update id={} failed: {}", r.id, e.getMessage());
                }
            }
            log.info("[EventExtraction] batch {}/{} done input={} updated={}",
                    batchesRun, maxBatches, chunk.size(), results.size());
        }

        log.info("[EventExtraction] all done processed={} success={} batches={}",
                processed, success, batchesRun);

        // v8: 分类完成后串行触发 OCR 增强（仅 price_finance 官号候选）— 失败不影响主流程
        try {
            OcrEnrichmentService.Result ocr = ocrEnrichmentService.run(OcrEnrichmentService.DEFAULT_LIMIT);
            log.info("[EventExtraction] OCR enrichment chained: processed={} policy_hit={} failed={}",
                    ocr.processed, ocr.policyHit, ocr.failed);
        } catch (Exception e) {
            log.warn("[EventExtraction] OCR enrichment chain failed (ignored): {}", e.getMessage());
        }

        return new Result(processed, success, batchesRun);
    }

    public static class Result {
        public final int processed;
        public final int success;
        public final int batchesRun;
        public Result(int processed, int success, int batchesRun) {
            this.processed = processed; this.success = success; this.batchesRun = batchesRun;
        }
    }
}
