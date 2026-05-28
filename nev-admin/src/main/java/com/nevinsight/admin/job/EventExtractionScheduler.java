package com.nevinsight.admin.job;

import com.nevinsight.intelligence.service.NewsEventExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件分类调度：在新闻采集后 10 分钟跑（采集 cron 是 0 0 6,12,18,23，本 cron 是 0 10 6,12,18,23）。
 *
 * 也在每天 06:40 多跑一次兜底（采 industry 6:30 跑完后接上）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventExtractionScheduler {

    private final NewsEventExtractionService eventService;

    /** 采集后批量分类（涵盖 self+competitor 和 industry 两条采集线）。 */
    @Scheduled(cron = "${nevinsight.event.extract-cron:0 10 6,12,18,23 * * ?}", zone = "Asia/Shanghai")
    public void runAfterCollect() {
        log.info("[EventExtraction] 触发批量分类（采集后）");
        try {
            NewsEventExtractionService.Result r = eventService.extractPending(
                    NewsEventExtractionService.DEFAULT_MAX_BATCHES);
            log.info("[EventExtraction] 完成 processed={} success={} batches={}",
                    r.processed, r.success, r.batchesRun);
        } catch (Exception e) {
            log.error("[EventExtraction] 异常: {}", e.getMessage(), e);
        }
    }
}
