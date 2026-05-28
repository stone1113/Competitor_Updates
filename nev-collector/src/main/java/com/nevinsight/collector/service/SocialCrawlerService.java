package com.nevinsight.collector.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.collector.client.CrawlerServiceClient;
import com.nevinsight.collector.config.CrawlerProperties;
import com.nevinsight.model.entity.core.BrandKeywordConfig;
import com.nevinsight.model.entity.core.CrawlerTask;
import com.nevinsight.model.mapper.core.BrandKeywordConfigMapper;
import com.nevinsight.model.mapper.core.CrawlerTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialCrawlerService {

    private final CrawlerProperties props;
    private final CrawlerServiceClient client;
    private final CrawlerTaskMapper taskMapper;
    private final BrandKeywordConfigMapper keywordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> ALL_PLATFORMS = Arrays.asList("xhs", "dy", "ks", "bili", "wb");

    /** 应用过 disabled-platforms 配置后的活跃平台列表 */
    public List<String> activePlatforms() {
        List<String> disabled = props.getDisabledPlatforms() == null ? java.util.Collections.emptyList() : props.getDisabledPlatforms();
        List<String> out = new ArrayList<>();
        for (String p : ALL_PLATFORMS) if (!disabled.contains(p)) out.add(p);
        return out;
    }

    /**
     * Trigger crawls for a brand across one or more platforms.
     * Returns the persisted CrawlerTask records (one per platform).
     */
    public List<CrawlerTask> trigger(
            String brand,
            List<String> platforms,
            List<String> keywords,
            Integer maxNotes,
            Boolean enableComments,
            String loginType) {

        if (!props.isEnabled()) {
            throw new IllegalStateException("crawler integration is disabled (nevinsight.crawler.enabled=false)");
        }
        if (brand == null || brand.isEmpty()) throw new IllegalArgumentException("brand is required");
        // 关键词为空时，从 brand_keyword_config 加载启用的关键词
        if (keywords == null || keywords.isEmpty()) {
            keywords = lookupKeywords(brand);
            if (keywords.isEmpty()) {
                throw new IllegalArgumentException("brand " + brand + " has no enabled keywords configured");
            }
            log.info("[Crawler] using {} preset keywords for brand={}: {}", keywords.size(), brand, keywords);
        }

        List<String> targetPlatforms = (platforms == null || platforms.isEmpty()) ? activePlatforms() : platforms;
        // 过滤掉被屏蔽的平台
        List<String> disabled = props.getDisabledPlatforms() == null ? java.util.Collections.emptyList() : props.getDisabledPlatforms();
        if (!disabled.isEmpty()) {
            List<String> filtered = new ArrayList<>();
            for (String p : targetPlatforms) {
                if (disabled.contains(p)) {
                    log.warn("[Crawler] platform '{}' is disabled (config), skipped", p);
                } else {
                    filtered.add(p);
                }
            }
            targetPlatforms = filtered;
            if (targetPlatforms.isEmpty()) {
                throw new IllegalArgumentException("no active platform after filtering disabled: " + disabled);
            }
        }
        int max = maxNotes != null ? maxNotes : props.getDefaultMaxNotes();
        boolean comments = enableComments == null || enableComments;
        String login = (loginType == null || loginType.isEmpty()) ? "cookie" : loginType;

        String keywordsJson;
        try {
            keywordsJson = objectMapper.writeValueAsString(keywords);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize keywords", e);
        }

        List<CrawlerTask> created = new ArrayList<>();
        for (String platform : targetPlatforms) {
            CrawlerTask task = new CrawlerTask();
            task.setBrandName(brand);
            task.setPlatform(platform);
            task.setKeywords(keywordsJson);
            task.setMaxNotes(max);
            task.setEnableComments(comments);
            task.setLoginType(login);
            task.setStatus("PENDING");

            try {
                String remoteId = client.triggerCrawl(platform, brand, keywords, max, comments, login);
                task.setRemoteTaskId(remoteId);
                task.setStatus("RUNNING");
                task.setStartedAt(System.currentTimeMillis());
                taskMapper.insert(task);
                final Long pollTaskId = task.getId();
                final String pollRemoteId = remoteId;
                Thread poller = new Thread(
                        () -> schedulePolling(pollTaskId, pollRemoteId),
                        "crawler-poll-" + pollTaskId);
                poller.setDaemon(true);
                poller.start();
            } catch (Exception e) {
                log.error("[Crawler] trigger failed platform={} brand={} err={}", platform, brand, e.getMessage());
                task.setStatus("FAILED");
                task.setErrorMessage("trigger failed: " + e.getMessage());
                task.setFinishedAt(System.currentTimeMillis());
                taskMapper.insert(task);
            }
            created.add(task);
        }
        return created;
    }

    /** Background poller for a single task (called from a dedicated Thread). */
    public void schedulePolling(Long taskId, String remoteTaskId) {
        log.info("[Crawler] poll start taskId={} remote={}", taskId, remoteTaskId);
        long deadline = System.currentTimeMillis() + props.getPollMaxDurationMs();
        int notFoundCount = 0;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(props.getPollIntervalMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }

            Map<String, Object> remote = client.getTask(remoteTaskId);
            if (remote == null) {
                log.debug("[Crawler] poll: remote task {} not yet visible (transient)", remoteTaskId);
                continue;
            }
            // 服务存活但找不到任务 → Python 重启后内存丢失
            if (Boolean.TRUE.equals(remote.get("_not_found"))) {
                notFoundCount++;
                if (notFoundCount >= 3) {
                    CrawlerTask task = taskMapper.selectById(taskId);
                    if (task != null && !isTerminal(task.getStatus())) {
                        task.setStatus("FAILED");
                        task.setErrorMessage("Python 爬虫服务已重启，任务状态丢失（孤儿任务）");
                        task.setFinishedAt(System.currentTimeMillis());
                        taskMapper.updateById(task);
                    }
                    log.warn("[Crawler] task {} (remote={}) orphaned after Python restart", taskId, remoteTaskId);
                    return;
                }
                continue;
            }
            notFoundCount = 0;

            String status = String.valueOf(remote.getOrDefault("status", "RUNNING"));
            CrawlerTask task = taskMapper.selectById(taskId);
            if (task == null) return; // deleted

            task.setStatus(status);
            Object errMsg = remote.get("error_message");
            if (errMsg != null) {
                String s = String.valueOf(errMsg);
                task.setErrorMessage(s.length() > 1900 ? s.substring(0, 1900) : s);
            }
            Object dur = remote.get("duration_sec");
            if (dur instanceof Number) {
                task.setDurationSec(((Number) dur).intValue());
            }
            taskMapper.updateById(task);

            if (isTerminal(status)) {
                if (task.getFinishedAt() == null) {
                    task.setFinishedAt(System.currentTimeMillis());
                    taskMapper.updateById(task);
                }
                log.info("[Crawler] task {} (remote={}) finished status={}", taskId, remoteTaskId, status);
                return;
            }
        }
        // Timed out polling — mark TIMEOUT locally
        CrawlerTask task = taskMapper.selectById(taskId);
        if (task != null && !isTerminal(task.getStatus())) {
            task.setStatus("TIMEOUT");
            task.setFinishedAt(System.currentTimeMillis());
            task.setErrorMessage("local polling exceeded " + props.getPollMaxDurationMs() + "ms");
            taskMapper.updateById(task);
        }
    }

    public List<CrawlerTask> recentTasks(int limit) {
        Page<CrawlerTask> page = new Page<>(1, Math.max(1, Math.min(limit, 200)));
        QueryWrapper<CrawlerTask> qw = new QueryWrapper<>();
        qw.orderByDesc("id");
        return taskMapper.selectPage(page, qw).getRecords();
    }

    public void deleteById(Long id) {
        taskMapper.deleteById(id);
    }

    /** 删除全部任务历史。includeRunning=false 时保留 RUNNING/PENDING 任务（避免轮询线程更新已删除行）。 */
    public int deleteAll(boolean includeRunning) {
        QueryWrapper<CrawlerTask> qw = new QueryWrapper<>();
        if (!includeRunning) {
            qw.notIn("status", "RUNNING", "PENDING");
        }
        return taskMapper.delete(qw);
    }

    public CrawlerTask getById(Long id) {
        return taskMapper.selectById(id);
    }

    /** Load enabled keywords for a brand from brand_keyword_config. */
    public List<String> lookupKeywords(String brand) {
        QueryWrapper<BrandKeywordConfig> qw = new QueryWrapper<>();
        qw.eq("brand_name", brand).eq("enabled", 1).orderByAsc("id");
        List<BrandKeywordConfig> rows = keywordMapper.selectList(qw);
        List<String> out = new ArrayList<>();
        for (BrandKeywordConfig r : rows) out.add(r.getKeyword());
        return out;
    }

    /** All distinct enabled brands from brand_keyword_config.
     *  本品牌（props.primaryBrand）排在第一位，其余按字母序。 */
    public List<String> listEnabledBrands() {
        QueryWrapper<BrandKeywordConfig> qw = new QueryWrapper<>();
        qw.select("DISTINCT brand_name").eq("enabled", 1);
        List<Object> rows = keywordMapper.selectObjs(qw);
        List<String> out = new ArrayList<>();
        for (Object r : rows) if (r != null) out.add(String.valueOf(r));
        return reorderWithPrimary(out);
    }

    /** 把本品牌挪到第一位（如果存在于列表中） */
    private List<String> reorderWithPrimary(List<String> brands) {
        String primary = props.getPrimaryBrand();
        if (primary == null || primary.isEmpty() || !brands.contains(primary)) return brands;
        List<String> out = new ArrayList<>(brands.size());
        out.add(primary);
        for (String b : brands) if (!primary.equals(b)) out.add(b);
        return out;
    }

    private boolean isTerminal(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "TIMEOUT".equals(status);
    }

    /**
     * 应用启动时回收孤儿任务：
     * 上次进程退出时，所有 RUNNING/PENDING 任务的轮询线程都丢失了。
     * 启动时扫描这些任务：
     *   - 如有 remoteTaskId → 查 Python 服务，存活则重启轮询，否则置 FAILED
     *   - 无 remoteTaskId → 直接置 FAILED（触发瞬间应用就死了）
     */
    @EventListener(ContextRefreshedEvent.class)
    public void recoverOrphanTasksOnStartup() {
        try {
            QueryWrapper<CrawlerTask> qw = new QueryWrapper<>();
            qw.in("status", "RUNNING", "PENDING", "WAITING_LOGIN");
            List<CrawlerTask> orphans = taskMapper.selectList(qw);
            if (orphans.isEmpty()) return;
            log.info("[Crawler] startup: found {} orphan task(s) from previous run", orphans.size());

            for (CrawlerTask t : orphans) {
                if (t.getRemoteTaskId() == null || t.getRemoteTaskId().isEmpty()) {
                    t.setStatus("FAILED");
                    t.setErrorMessage("应用重启前未取得远程 task_id");
                    t.setFinishedAt(System.currentTimeMillis());
                    taskMapper.updateById(t);
                    continue;
                }
                Map<String, Object> remote = client.getTask(t.getRemoteTaskId());
                if (remote == null || Boolean.TRUE.equals(remote.get("_not_found"))) {
                    t.setStatus("FAILED");
                    t.setErrorMessage("应用重启后远程任务不可达");
                    t.setFinishedAt(System.currentTimeMillis());
                    taskMapper.updateById(t);
                    log.info("[Crawler] task {} marked FAILED (remote not found)", t.getId());
                    continue;
                }
                String remoteStatus = String.valueOf(remote.getOrDefault("status", "RUNNING"));
                if (isTerminal(remoteStatus)) {
                    // Python 已结束：直接同步终态
                    t.setStatus(remoteStatus);
                    Object dur = remote.get("duration_sec");
                    if (dur instanceof Number) t.setDurationSec(((Number) dur).intValue());
                    Object err = remote.get("error_message");
                    if (err != null) {
                        String s = String.valueOf(err);
                        t.setErrorMessage(s.length() > 1900 ? s.substring(0, 1900) : s);
                    }
                    t.setFinishedAt(System.currentTimeMillis());
                    taskMapper.updateById(t);
                    log.info("[Crawler] task {} synced to {} (Python had finished while app was down)", t.getId(), remoteStatus);
                } else {
                    // Python 还在跑：重启轮询线程
                    final Long pollTaskId = t.getId();
                    final String pollRemoteId = t.getRemoteTaskId();
                    Thread poller = new Thread(
                            () -> schedulePolling(pollTaskId, pollRemoteId),
                            "crawler-poll-recover-" + pollTaskId);
                    poller.setDaemon(true);
                    poller.start();
                    log.info("[Crawler] task {} resumed polling (Python still RUNNING)", t.getId());
                }
            }
        } catch (Exception e) {
            log.error("[Crawler] startup recovery failed: {}", e.getMessage(), e);
        }
    }

    // ==================== 全量串行轮询 ====================

    @lombok.Data
    public static class RunAllState {
        private boolean running;
        private boolean lastFinished;
        private String currentBrand;
        private String currentPlatform;
        private int done;
        private int total;
        private long startedAt;
        private long finishedAt;
        private String lastError;
        private List<String> log = new ArrayList<>();
    }

    private final AtomicBoolean runAllRunning = new AtomicBoolean(false);
    private volatile RunAllState runAllState = new RunAllState();

    public RunAllState getRunAllStatus() {
        return runAllState;
    }

    /** Trigger a full sequential sweep. Returns false if one is already running.
     *  Throws IllegalStateException if crawler service unreachable (pre-check).
     */
    public synchronized boolean triggerRunAll() {
        if (!client.isHealthy()) {
            throw new IllegalStateException(
                    "本地爬虫服务（http://host.docker.internal:8091）不可达。" +
                    "请先在 Mac 上启动：cd crawler-service && ./start-local.sh");
        }
        if (!runAllRunning.compareAndSet(false, true)) {
            log.info("[RunAll] already in progress, ignored");
            return false;
        }
        Thread t = new Thread(this::runAllSequential, "crawler-run-all");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void runAllSequential() {
        RunAllState st = new RunAllState();
        st.setRunning(true);
        st.setStartedAt(System.currentTimeMillis());
        runAllState = st;
        try {
            List<String> brands = listEnabledBrands();
            if (brands.isEmpty()) {
                st.setLastError("brand_keyword_config 无启用品牌");
                return;
            }
            List<String> active = activePlatforms();
            int total = brands.size() * active.size();
            st.setTotal(total);
            int done = 0;
            for (String brand : brands) {
                List<String> kws = lookupKeywords(brand);
                if (kws.isEmpty()) {
                    st.getLog().add(String.format("[skip] %s 无启用关键词", brand));
                    done += active.size();
                    st.setDone(done);
                    continue;
                }
                for (String platform : active) {
                    st.setCurrentBrand(brand);
                    st.setCurrentPlatform(platform);
                    st.setDone(done);
                    long t0 = System.currentTimeMillis();
                    try {
                        List<CrawlerTask> tasks = trigger(brand, Collections.singletonList(platform), kws, 20, true, "cookie");
                        if (!tasks.isEmpty() && tasks.get(0).getId() != null) {
                            String finalStatus = waitForTerminal(tasks.get(0).getId());
                            long elapsed = (System.currentTimeMillis() - t0) / 1000;
                            st.getLog().add(String.format("[%d/%d] %s/%s %s (%ds)",
                                    done + 1, total, brand, platform, finalStatus, elapsed));
                        }
                    } catch (Exception e) {
                        log.error("[RunAll] {}/{} 触发失败 {}", brand, platform, e.getMessage());
                        st.getLog().add(String.format("[%d/%d] %s/%s ERROR %s",
                                done + 1, total, brand, platform, e.getMessage()));
                    }
                    done++;
                    st.setDone(done);
                    if (st.getLog().size() > 100) st.getLog().remove(0);
                }
            }
            st.setLastFinished(true);
        } catch (Exception e) {
            st.setLastError(e.getMessage());
            log.error("[RunAll] aborted: {}", e.getMessage(), e);
        } finally {
            st.setRunning(false);
            st.setFinishedAt(System.currentTimeMillis());
            st.setCurrentBrand(null);
            st.setCurrentPlatform(null);
            runAllRunning.set(false);
            log.info("[RunAll] finished done={}/{} error={}", st.getDone(), st.getTotal(), st.getLastError());
        }
    }

    /** Block until task reaches terminal status, or hit per-task timeout. Returns final status. */
    private String waitForTerminal(Long taskId) {
        long deadline = System.currentTimeMillis() + props.getPerTaskWaitMs();
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(5000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            }
            CrawlerTask t = taskMapper.selectById(taskId);
            if (t == null) return "DELETED";
            if (isTerminal(t.getStatus())) return t.getStatus();
        }
        return "TIMEOUT_LOCAL";
    }

    // 定时调度由 CrawlerScheduleManager 动态管理（基于 crawler_schedule_config 表）
}
