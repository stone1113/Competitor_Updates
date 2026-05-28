package com.nevinsight.collector.service;

import com.nevinsight.model.entity.core.CrawlerScheduleConfig;
import com.nevinsight.model.mapper.core.CrawlerScheduleConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态管理爬虫定时任务。
 * 任务表 crawler_schedule_config 只有一行 (id=1)；启停 + cron 改完即重新调度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerScheduleManager {

    private final CrawlerScheduleConfigMapper configMapper;
    private final SocialCrawlerService crawlerService;
    @Qualifier("crawlerTaskScheduler")
    private final TaskScheduler scheduler;

    private volatile ScheduledFuture<?> currentTask;
    private volatile String currentCron;

    /** Spring 启动后加载并调度 */
    @EventListener(ContextRefreshedEvent.class)
    public void onStartup() {
        reschedule();
    }

    /** 取当前配置，必要时插入默认行 */
    public CrawlerScheduleConfig getConfig() {
        CrawlerScheduleConfig c = configMapper.selectById(1);
        if (c == null) {
            c = new CrawlerScheduleConfig();
            c.setId(1);
            c.setEnabled(false);
            c.setCron("0 0 2 * * ?");
            c.setLastModifyTs(System.currentTimeMillis());
            configMapper.insert(c);
        }
        return c;
    }

    /** 更新配置并重新调度。返回更新后的配置。 */
    public synchronized CrawlerScheduleConfig updateConfig(Boolean enabled, String cron) {
        CrawlerScheduleConfig c = getConfig();
        if (enabled != null) c.setEnabled(enabled);
        if (cron != null && !cron.isEmpty()) {
            // 验证 cron 合法性
            try { CronExpression.parse(cron); }
            catch (Exception e) { throw new IllegalArgumentException("invalid cron: " + cron); }
            c.setCron(cron);
        }
        c.setLastModifyTs(System.currentTimeMillis());
        configMapper.updateById(c);
        reschedule();
        return c;
    }

    /** 当前调度状态：next fire time（如有） */
    public Long nextRunMillis() {
        CrawlerScheduleConfig c = getConfig();
        if (!Boolean.TRUE.equals(c.getEnabled())) return null;
        try {
            CronExpression expr = CronExpression.parse(c.getCron());
            LocalDateTime next = expr.next(LocalDateTime.now());
            return next == null ? null : next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    /** 取消旧调度，按当前配置（若 enabled）注册新调度 */
    public synchronized void reschedule() {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        CrawlerScheduleConfig c = getConfig();
        if (!Boolean.TRUE.equals(c.getEnabled())) {
            log.info("[Schedule] disabled — no task scheduled");
            currentCron = null;
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(c.getCron(), ZoneId.systemDefault());
            currentTask = scheduler.schedule(this::fireOnce, trigger);
            currentCron = c.getCron();
            log.info("[Schedule] scheduled cron='{}'", c.getCron());
        } catch (Exception e) {
            log.error("[Schedule] failed to schedule cron='{}' err={}", c.getCron(), e.getMessage());
        }
    }

    private void fireOnce() {
        log.info("[Schedule] cron fired → triggerRunAll");
        try {
            crawlerService.triggerRunAll();
        } catch (Exception e) {
            log.error("[Schedule] triggerRunAll failed: {}", e.getMessage(), e);
        }
    }

    /** 注册一个独立的 TaskScheduler，避免与默认 single-threaded scheduler 抢占 */
    @Configuration
    static class SchedulerConfig {
        @Bean(name = "crawlerTaskScheduler")
        public ThreadPoolTaskScheduler crawlerTaskScheduler() {
            ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
            s.setPoolSize(1);
            s.setThreadNamePrefix("crawler-sched-");
            s.setWaitForTasksToCompleteOnShutdown(false);
            s.initialize();
            return s;
        }
    }
}
