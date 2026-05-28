package com.nevinsight.admin.job;

import com.nevinsight.collector.service.OfficialAccountCrawlerService;
import com.nevinsight.collector.service.OfficialPostIngester;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 官方账号抓取 + ingest 调度。
 *
 *   - 抓取（crawl）：每天 4 次（07:00/13:00/19:00/00:00 — 跟 Bocha 错开 1 小时避免争抢爬虫）
 *   - Ingest：每天 4 次，在抓取后 15 分钟跑（确保 crawler-service 子进程已写完 weibo_note/douyin_aweme）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficialAccountScheduler {

    private final OfficialAccountCrawlerService crawlerService;
    private final OfficialPostIngester ingester;

    @Scheduled(cron = "${nevinsight.official.crawl-cron:0 0 7,13,19,0 * * ?}", zone = "Asia/Shanghai")
    public void crawl() {
        log.info("[OfficialScheduler] 触发官方账号抓取");
        try {
            OfficialAccountCrawlerService.CrawlResult r = crawlerService.crawlAll();
            log.info("[OfficialScheduler] crawl 完成 tasks={} accounts={}",
                    r.tasksFired, r.accountsCovered);
        } catch (Exception e) {
            log.error("[OfficialScheduler] crawl 异常: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "${nevinsight.official.ingest-cron:0 15 7,13,19,0 * * ?}", zone = "Asia/Shanghai")
    public void ingest() {
        log.info("[OfficialScheduler] 触发官方帖 ingest");
        try {
            OfficialPostIngester.IngestResult r = ingester.ingestAll();
            log.info("[OfficialScheduler] ingest 完成 scanned={} inserted={}",
                    r.scanned, r.inserted);
        } catch (Exception e) {
            log.error("[OfficialScheduler] ingest 异常: {}", e.getMessage(), e);
        }
    }
}
