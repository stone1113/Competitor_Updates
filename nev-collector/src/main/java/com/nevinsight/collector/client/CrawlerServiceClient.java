package com.nevinsight.collector.client;

import com.nevinsight.collector.config.CrawlerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client to the Python crawler service (FastAPI).
 *
 * Endpoints:
 *   POST /crawl
 *   GET  /tasks/{task_id}
 *   GET  /health
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerServiceClient {

    private final CrawlerProperties props;
    private RestTemplate http;

    @PostConstruct
    void init() {
        this.http = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .build();
    }

    /**
     * Trigger a single-platform crawl. Returns the remote task_id (UUID).
     */
    public String triggerCrawl(
            String platform,
            String brand,
            List<String> keywords,
            int maxNotes,
            boolean enableComments,
            String loginType) {
        return triggerCrawlInternal(platform, brand, "search", keywords,
                java.util.Collections.emptyList(), maxNotes, enableComments, loginType);
    }

    /**
     * Trigger creator-mode crawl (按官方账号抓最新 N 条发帖).
     */
    public String triggerCreatorCrawl(
            String platform,
            String brand,
            List<String> creatorIds,
            int maxNotes,
            String loginType) {
        return triggerCrawlInternal(platform, brand, "creator",
                java.util.Collections.emptyList(), creatorIds, maxNotes, false, loginType);
    }

    private String triggerCrawlInternal(
            String platform, String brand, String mode,
            List<String> keywords, List<String> creatorIds,
            int maxNotes, boolean enableComments, String loginType) {

        Map<String, Object> body = new HashMap<>();
        body.put("platform", platform);
        body.put("brand", brand);
        body.put("mode", mode);
        body.put("keywords", keywords);
        body.put("creator_ids", creatorIds);
        body.put("max_notes", maxNotes);
        body.put("enable_comments", enableComments);
        body.put("login_type", loginType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(props.getBaseUrl() + "/crawl", req, Map.class);
        if (resp == null || resp.get("task_id") == null) {
            throw new IllegalStateException("crawler /crawl response missing task_id: " + resp);
        }
        return String.valueOf(resp.get("task_id"));
    }

    /**
     * Get the latest status of a remote task.
     * Returns null on transient failure (connection refused / timeout / etc).
     * Returns a Map with key "_not_found"=true when service explicitly returns 404
     * (i.e. service alive but task unknown — happens after Python service restart).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTask(String remoteTaskId) {
        try {
            return http.getForObject(props.getBaseUrl() + "/tasks/" + remoteTaskId, Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            Map<String, Object> r = new HashMap<>();
            r.put("_not_found", Boolean.TRUE);
            return r;
        } catch (Exception e) {
            log.warn("[CrawlerClient] getTask failed taskId={} err={}", remoteTaskId, e.getMessage());
            return null;
        }
    }

    /** Manually trigger a re-login (qrcode + visible browser) for a platform. Returns remote task_id. */
    public String triggerLogin(String platform) {
        Map<String, Object> body = new HashMap<>();
        body.put("platform", platform);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = http.postForObject(props.getBaseUrl() + "/login", req, Map.class);
        if (resp == null || resp.get("task_id") == null) {
            throw new IllegalStateException("login response missing task_id: " + resp);
        }
        return String.valueOf(resp.get("task_id"));
    }

    public boolean isHealthy() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = http.getForObject(props.getBaseUrl() + "/health", Map.class);
            return r != null && "ok".equals(r.get("status"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 汽车之家车型参数抓取。返回 [{success, series_id, specs:[...], error, source}, ...]。
     * 同步阻塞，8 车型 ~30-60 秒。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> scrapeAutohome(List<String> seriesIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("series_ids", seriesIds);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        // 长时延：临时把 read timeout 拉高
        RestTemplate longHttp = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMinutes(5))
                .build();

        try {
            Map<String, Object> resp = longHttp.postForObject(
                    props.getBaseUrl() + "/scrape-autohome", req, Map.class);
            if (resp == null || resp.get("items") == null) {
                log.warn("[CrawlerClient] autohome response missing items: {}", resp);
                return java.util.Collections.emptyList();
            }
            return (List<Map<String, Object>>) resp.get("items");
        } catch (Exception e) {
            log.error("[CrawlerClient] scrapeAutohome failed: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
