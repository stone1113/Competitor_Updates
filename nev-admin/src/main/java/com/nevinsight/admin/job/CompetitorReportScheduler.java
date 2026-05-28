package com.nevinsight.admin.job;

import com.nevinsight.collector.service.AutohomeSyncService;
import com.nevinsight.intelligence.service.AutohomeKnowledgeSyncService;
import com.nevinsight.report.pipeline.CompetitorReportPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 竞品分析两个定时任务：
 *  - 每天 08:30：生成 + 推送竞品分析日报
 *  - 每周一 03:00：抓汽车之家参数 + 上传 RAGFlow
 *
 * cron 由配置项控制：
 *  - nevinsight.competitor.cron
 *  - nevinsight.competitor.autohome-sync-cron
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompetitorReportScheduler {

    private final CompetitorReportPipeline pipeline;
    private final AutohomeSyncService autohomeSyncService;
    private final AutohomeKnowledgeSyncService autohomeKnowledgeSyncService;

    /** 每天竞品日报。默认 08:30 北京时间。 */
    @Scheduled(cron = "${nevinsight.competitor.cron:0 30 8 * * ?}", zone = "Asia/Shanghai")
    public void dailyReport() {
        log.info("[CompetitorScheduler] 触发竞品日报");
        try {
            CompetitorReportPipeline.Result r = pipeline.run(null, true);
            log.info("[CompetitorScheduler] 竞品日报完成 success={} pushed={} talkingPoints={}",
                    r.isSuccess(), r.isPushed(), r.getTalkingPointsCount());
        } catch (Exception e) {
            log.error("[CompetitorScheduler] 竞品日报异常: {}", e.getMessage(), e);
        }
    }

    /** 每周一汽车之家参数同步。默认周一 03:00。 */
    @Scheduled(cron = "${nevinsight.competitor.autohome-sync-cron:0 0 3 ? * MON}", zone = "Asia/Shanghai")
    public void weeklyAutohomeSync() {
        log.info("[CompetitorScheduler] 触发汽车之家参数同步");
        try {
            AutohomeSyncService.SyncResult sr = autohomeSyncService.syncAll();
            log.info("[CompetitorScheduler] autohome 抓取完成 success={} rows={} failed={}",
                    sr.successSeriesCount, sr.totalRows, sr.failedSeriesIds);
            if (sr.successSeriesCount > 0) {
                AutohomeKnowledgeSyncService.Result kr = autohomeKnowledgeSyncService.exportAndUpload();
                log.info("[CompetitorScheduler] RAGFlow 上传 success={} sheets={} msg={}",
                        kr.success, kr.sheetsWritten, kr.message);
            }
        } catch (Exception e) {
            log.error("[CompetitorScheduler] autohome sync 异常: {}", e.getMessage(), e);
        }
    }
}
