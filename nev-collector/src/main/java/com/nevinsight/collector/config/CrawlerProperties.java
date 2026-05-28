package com.nevinsight.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nevinsight.crawler")
public class CrawlerProperties {

    /** Whether the social crawler integration is enabled. */
    private boolean enabled = true;

    /** Base URL of the Python crawler service (e.g. http://crawler:8091). */
    private String baseUrl = "http://crawler:8091";

    /** HTTP connect timeout (ms) for client → crawler service. */
    private int connectTimeoutMs = 5000;

    /** HTTP read timeout (ms). Crawl jobs can be long; this only covers the trigger/status calls. */
    private int readTimeoutMs = 30000;

    /** Default per-task max notes when caller omits it. */
    private int defaultMaxNotes = 20;

    /** Polling interval for in-flight tasks (ms). */
    private long pollIntervalMs = 10_000;

    /** Hard ceiling for polling (ms). After this, mark the task as TIMEOUT locally. */
    private long pollMaxDurationMs = 60 * 60 * 1000L;

    /** Enable the periodic "all brands × all platforms" run. */
    private boolean scheduleEnabled = false;

    /** Cron expression for the periodic run. Default: 02:00 daily. */
    private String scheduleCron = "0 0 2 * * ?";

    /** Per-task wait timeout in the sequential run (ms). 30 min by default. */
    private long perTaskWaitMs = 30 * 60 * 1000L;

    /** 临时屏蔽的平台（platform code 列表，如 ["xhs"]）。被屏蔽的平台触发会被静默跳过。 */
    private java.util.List<String> disabledPlatforms = new java.util.ArrayList<>();

    /** 本品牌名称。在"一键全量爬取"和定时任务中会被排在第一位。 */
    private String primaryBrand = "猛士";
}
