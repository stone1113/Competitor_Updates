package com.nevinsight.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 新闻采集定时任务 v3（多频次）。
 *
 * - 本品 + 竞品：每天 4 次 (06:00 / 12:00 / 18:00 / 23:00) — 保证日报 08:30 推送时 32h 窗口有 4 次刷新
 * - 行业新闻：每天 1 次 (06:30) — 量大且新闻同质化高，不必频繁
 *
 * 单次 cron 都可单独覆盖（环境变量）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectorScheduler {

    private final WebNewsCollectorService newsCollector;

    /** 本品 + 竞品：每天 4 次。 */
    @Scheduled(cron = "${nevinsight.crawler.news-cron-self-competitor:0 0 6,12,18,23 * * ?}",
               zone = "Asia/Shanghai")
    public void runSelfCompetitor() {
        log.info("[NewsScheduler] 触发 self+competitor 采集...");
        try {
            int n = newsCollector.collectByCategory("brand"); // brand = self+competitor 兼容值
            log.info("[NewsScheduler] self+competitor 完成 inserted={}", n);
        } catch (Exception e) {
            log.error("[NewsScheduler] self+competitor 失败: {}", e.getMessage(), e);
        }
    }

    /** 行业：每天 1 次。 */
    @Scheduled(cron = "${nevinsight.crawler.news-cron-industry:0 30 6 * * ?}",
               zone = "Asia/Shanghai")
    public void runIndustry() {
        log.info("[NewsScheduler] 触发 industry 采集...");
        try {
            int n = newsCollector.collectByCategory("industry");
            log.info("[NewsScheduler] industry 完成 inserted={}", n);
        } catch (Exception e) {
            log.error("[NewsScheduler] industry 失败: {}", e.getMessage(), e);
        }
    }
}
