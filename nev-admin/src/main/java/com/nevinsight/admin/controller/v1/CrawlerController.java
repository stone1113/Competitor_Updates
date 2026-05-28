package com.nevinsight.admin.controller.v1;

import com.nevinsight.collector.client.CrawlerServiceClient;
import com.nevinsight.collector.service.CrawlerScheduleManager;
import com.nevinsight.collector.service.SocialCrawlerService;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.CrawlerScheduleConfig;
import com.nevinsight.model.entity.core.CrawlerTask;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final SocialCrawlerService crawlerService;
    private final CrawlerServiceClient client;
    private final CrawlerScheduleManager scheduleManager;

    @PostMapping("/trigger")
    public ApiResponse<List<CrawlerTask>> trigger(@RequestBody TriggerReq req) {
        List<CrawlerTask> tasks = crawlerService.trigger(
                req.getBrand(),
                req.getPlatforms(),
                req.getKeywords(),
                req.getMaxNotes(),
                req.getEnableComments(),
                req.getLoginType());
        return ApiResponse.success(tasks);
    }

    @GetMapping("/tasks")
    public ApiResponse<List<CrawlerTask>> tasks(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(crawlerService.recentTasks(limit));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<CrawlerTask> task(@PathVariable Long id) {
        CrawlerTask t = crawlerService.getById(id);
        if (t == null) return ApiResponse.error(404, "task not found");
        return ApiResponse.success(t);
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        crawlerService.deleteById(id);
        return ApiResponse.success();
    }

    /** 清空任务历史（不含正在 RUNNING 的任务，避免影响轮询） */
    @DeleteMapping("/tasks")
    public ApiResponse<Map<String, Object>> deleteAllTasks(
            @RequestParam(defaultValue = "false") boolean includeRunning) {
        int n = crawlerService.deleteAll(includeRunning);
        Map<String, Object> r = new HashMap<>();
        r.put("deleted", n);
        return ApiResponse.success(r);
    }

    @PostMapping("/tasks/{id}/rerun")
    public ApiResponse<List<CrawlerTask>> rerun(@PathVariable Long id) {
        CrawlerTask t = crawlerService.getById(id);
        if (t == null) return ApiResponse.error(404, "task not found");
        List<String> keywords;
        try {
            keywords = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(t.getKeywords() == null ? "[]" : t.getKeywords(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return ApiResponse.error(400, "keywords parse error: " + e.getMessage());
        }
        try {
            List<CrawlerTask> created = crawlerService.trigger(
                    t.getBrandName(),
                    java.util.Collections.singletonList(t.getPlatform()),
                    keywords,
                    t.getMaxNotes(),
                    t.getEnableComments(),
                    t.getLoginType());
            return ApiResponse.success(created);
        } catch (Exception e) {
            return ApiResponse.error(503, "rerun failed: " + e.getMessage());
        }
    }

    @GetMapping("/tasks/{id}/logs")
    public ApiResponse<Map<String, Object>> taskLogs(@PathVariable Long id) {
        CrawlerTask t = crawlerService.getById(id);
        if (t == null) return ApiResponse.error(404, "task not found");
        Map<String, Object> r = new HashMap<>();
        r.put("status", t.getStatus());
        r.put("errorMessage", t.getErrorMessage());
        r.put("logs", java.util.Collections.emptyList());
        if (t.getRemoteTaskId() != null) {
            Map<String, Object> remote = client.getTask(t.getRemoteTaskId());
            if (remote != null) {
                r.put("logs", remote.getOrDefault("log_tail", java.util.Collections.emptyList()));
                r.put("status", remote.getOrDefault("status", t.getStatus()));
                if (remote.get("error_message") != null) r.put("errorMessage", remote.get("error_message"));
            }
        }
        return ApiResponse.success(r);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> r = new HashMap<>();
        r.put("crawler_service_reachable", client.isHealthy());
        return ApiResponse.success(r);
    }

    /** 当前活跃（未被 disabled-platforms 屏蔽）的平台列表 */
    @GetMapping("/active-platforms")
    public ApiResponse<List<String>> activePlatforms() {
        return ApiResponse.success(crawlerService.activePlatforms());
    }

    /** 触发指定平台的重新登录（弹出浏览器扫码） */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestParam String platform) {
        try {
            String taskId = client.triggerLogin(platform);
            Map<String, Object> r = new HashMap<>();
            r.put("remoteTaskId", taskId);
            r.put("message", platform + " 登录任务已启动，请在 Mac 上扫码");
            return ApiResponse.success(r);
        } catch (Exception e) {
            return ApiResponse.error(503, "触发失败：" + e.getMessage());
        }
    }

    /** 触发"全量串行"：所有启用品牌 × 所有 5 平台，逐个排队，等上一个跑完再起下一个 */
    @PostMapping("/run-all")
    public ApiResponse<Map<String, Object>> runAll() {
        try {
            boolean started = crawlerService.triggerRunAll();
            Map<String, Object> r = new HashMap<>();
            r.put("started", started);
            r.put("message", started ? "已启动全量串行爬取" : "已有任务在运行，忽略本次");
            return ApiResponse.success(r);
        } catch (IllegalStateException e) {
            return ApiResponse.error(503, e.getMessage());
        }
    }

    @GetMapping("/run-all/status")
    public ApiResponse<SocialCrawlerService.RunAllState> runAllStatus() {
        return ApiResponse.success(crawlerService.getRunAllStatus());
    }

    @GetMapping("/schedule")
    public ApiResponse<Map<String, Object>> getSchedule() {
        CrawlerScheduleConfig c = scheduleManager.getConfig();
        Map<String, Object> r = new HashMap<>();
        r.put("enabled", c.getEnabled());
        r.put("cron", c.getCron());
        r.put("lastModifyTs", c.getLastModifyTs());
        r.put("nextRunAt", scheduleManager.nextRunMillis());
        return ApiResponse.success(r);
    }

    @PutMapping("/schedule")
    public ApiResponse<Map<String, Object>> updateSchedule(@RequestBody ScheduleReq req) {
        try {
            CrawlerScheduleConfig c = scheduleManager.updateConfig(req.getEnabled(), req.getCron());
            Map<String, Object> r = new HashMap<>();
            r.put("enabled", c.getEnabled());
            r.put("cron", c.getCron());
            r.put("nextRunAt", scheduleManager.nextRunMillis());
            return ApiResponse.success(r);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @Data
    public static class ScheduleReq {
        private Boolean enabled;
        private String cron;
    }

    @Data
    public static class TriggerReq {
        private String brand;
        private List<String> platforms;
        private List<String> keywords;
        private Integer maxNotes;
        private Boolean enableComments;
        private String loginType;
    }
}
