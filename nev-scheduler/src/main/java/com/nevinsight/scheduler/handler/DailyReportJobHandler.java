package com.nevinsight.scheduler.handler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyReportJobHandler {

    /**
     * 每日08:30 生成草稿日报
     * XXL-JOB CRON: 0 30 8 * * ?
     */
    @XxlJob("dailyReportDraftHandler")
    public void generateDraft() {
        log.info("[XXL-JOB] 开始生成日报草稿...");
        // TODO: 注入 DailyReportPipeline 调用 run(null, true, true)
    }

    /**
     * 手动触发：推送到生产群
     */
    @XxlJob("dailyReportProductionHandler")
    public void pushProduction() {
        log.info("[XXL-JOB] 推送日报到生产群...");
        // TODO: 读取当日草稿，推送到生产 webhook
    }

    /**
     * 数据采集任务
     */
    @XxlJob("collectorJobHandler")
    public void runCollector() {
        log.info("[XXL-JOB] 开始数据采集...");
        // TODO: 触发 BochaAI 采集
    }
}
