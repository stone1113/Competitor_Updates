package com.nevinsight.intelligence.service;

import com.nevinsight.intelligence.agent.DeepBenchmarkAgent;
import com.nevinsight.intelligence.agent.DeepBenchmarkAgent.Dimension;
import com.nevinsight.intelligence.dto.AnalystResult;
import com.nevinsight.intelligence.dto.BenchmarkContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * v9 深度对标编排服务。
 *
 * 流程：
 *   1. DataCollector 拉数据（同步，~50ms）
 *   2. 5 个 Analyst 并行（CompletableFuture.allOf，~30s 总）
 *   3. Synthesizer 串行综合（~30-40s）
 *
 * 任务管理：
 *   - 内存 ConcurrentHashMap<taskId, BenchmarkTask>
 *   - 1h 自动清理
 *   - 支持 SSE listener 推每步事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepBenchmarkService {

    /** 5 个 Analyst 并行线程池 */
    private final ExecutorService analystPool = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "deep-benchmark-analyst");
        t.setDaemon(true);
        return t;
    });

    /** SSE listener 推送独立池（避免阻塞 runTask）*/
    private final ExecutorService notifyPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "deep-benchmark-notify");
        t.setDaemon(true);
        return t;
    });

    /** 任务存活时长：1 小时 */
    private static final long TASK_TTL_MS = 3600_000L;

    private final ConcurrentMap<String, BenchmarkTask> tasks = new ConcurrentHashMap<>();

    private final DeepBenchmarkDataCollector dataCollector;
    private final DeepBenchmarkAgent agent;

    /**
     * 异步启动一个深度对标任务。
     * @param selfModel 本品车型名
     * @param competitorModels 竞品车型名列表（至少 1 个）
     * @return taskId（前端用此 ID 订阅 SSE）
     */
    public String startAsync(String selfModel, List<String> competitorModels) {
        if (selfModel == null || selfModel.isEmpty()) {
            throw new IllegalArgumentException("selfModel 不能为空");
        }
        if (competitorModels == null || competitorModels.isEmpty()) {
            throw new IllegalArgumentException("至少选择 1 个竞品");
        }
        String taskId = UUID.randomUUID().toString();
        BenchmarkTask task = new BenchmarkTask();
        task.setTaskId(taskId);
        task.setSelfModel(selfModel);
        task.setCompetitorModels(competitorModels);
        task.setStatus(Status.PENDING);
        task.setStartedAt(System.currentTimeMillis());
        tasks.put(taskId, task);

        // 异步跑
        CompletableFuture.runAsync(() -> runTask(task), analystPool);

        log.info("[DeepBenchmark] 任务启动 taskId={} self={} competitors={}",
                taskId, selfModel, competitorModels);
        return taskId;
    }

    public BenchmarkTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /** 注册 SSE listener（一旦有事件就推送） */
    public void subscribe(String taskId, Consumer<StepEvent> listener) {
        BenchmarkTask task = tasks.get(taskId);
        if (task == null) {
            listener.accept(StepEvent.of("UNKNOWN", "FAILED", "任务不存在: " + taskId, null));
            return;
        }
        task.getListeners().add(listener);
        // 重放历史事件（重连场景）
        for (StepEvent ev : task.getHistory()) {
            listener.accept(ev);
        }
    }

    public void unsubscribe(String taskId, Consumer<StepEvent> listener) {
        BenchmarkTask task = tasks.get(taskId);
        if (task != null) task.getListeners().remove(listener);
    }

    // ===== 核心编排 =====

    private void runTask(BenchmarkTask task) {
        try {
            // Step 1: DataCollector
            task.setStatus(Status.COLLECTING);
            pushEvent(task, StepEvent.of("DATA_COLLECTOR", "RUNNING", null, null));
            BenchmarkContext ctx = dataCollector.collect(task.getSelfModel(), task.getCompetitorModels());
            task.setContext(ctx);
            int paramCount = ctx.getModelDataMap().values().stream()
                    .mapToInt(md -> md.getSpecs().size()).sum();
            int salesCount = ctx.getModelDataMap().values().stream()
                    .mapToInt(md -> md.getRecentSales().size()).sum();
            int priceCount = ctx.getModelDataMap().values().stream()
                    .mapToInt(md -> md.getPriceEvents().size()).sum();
            Map<String, Object> collectorPayload = Map.of(
                    "models", ctx.getAllModels().size(),
                    "totalParams", paramCount,
                    "totalSalesSnapshots", salesCount,
                    "totalPriceEvents", priceCount);
            pushEvent(task, StepEvent.of("DATA_COLLECTOR", "DONE", null, collectorPayload));

            // Step 2: 5 Analyst 并行
            task.setStatus(Status.ANALYZING);
            Map<Dimension, AnalystResult> analystResults = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Dimension dim : Dimension.values()) {
                pushEvent(task, StepEvent.of(dim.name(), "RUNNING", null, null));
                CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                    AnalystResult r = agent.analyze(dim, ctx);
                    analystResults.put(dim, r);
                    task.getAnalystResults().put(dim.name(), r);
                    pushEvent(task, StepEvent.of(dim.name(), r.getError() == null ? "DONE" : "FAILED",
                            r.getError(), Map.of(
                                    "dimension", r.getDimension(),
                                    "winner", r.getWinner() == null ? "" : r.getWinner(),
                                    "scores", r.getScores(),
                                    "keyFindings", r.getKeyFindings(),
                                    "elapsedMs", r.getElapsedMs())));
                }, analystPool);
                futures.add(f);
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(180, TimeUnit.SECONDS);

            // Step 3: Synthesizer
            task.setStatus(Status.SYNTHESIZING);
            pushEvent(task, StepEvent.of("SYNTHESIZER", "RUNNING", null, null));
            // 按 enum 顺序传给 synthesizer 保持稳定
            LinkedHashMap<Dimension, AnalystResult> ordered = new LinkedHashMap<>();
            for (Dimension d : Dimension.values()) ordered.put(d, analystResults.get(d));
            String markdown = agent.synthesize(ctx, ordered);
            task.setReportMarkdown(markdown);
            pushEvent(task, StepEvent.of("SYNTHESIZER", "DONE", null,
                    Map.of("length", markdown.length())));

            // Done
            task.setStatus(Status.DONE);
            task.setFinishedAt(System.currentTimeMillis());
            pushEvent(task, StepEvent.of("TASK", "DONE", null,
                    Map.of("totalElapsedMs", task.getFinishedAt() - task.getStartedAt())));
        } catch (Exception e) {
            log.error("[DeepBenchmark] 任务失败 taskId={}: {}", task.getTaskId(), e.getMessage(), e);
            task.setStatus(Status.FAILED);
            task.setError(e.getMessage());
            task.setFinishedAt(System.currentTimeMillis());
            pushEvent(task, StepEvent.of("TASK", "FAILED", e.getMessage(), null));
        }
    }

    private void pushEvent(BenchmarkTask task, StepEvent event) {
        task.getHistory().add(event);
        for (Consumer<StepEvent> listener : task.getListeners()) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
                // listener 异常不影响其他
            }
        }
    }

    /** 每 10 分钟清一次过期任务 */
    @Scheduled(fixedRate = 600_000L)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<String, BenchmarkTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            BenchmarkTask t = it.next().getValue();
            long age = now - t.getStartedAt();
            if (age > TASK_TTL_MS) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) log.info("[DeepBenchmark] 清理过期任务 {} 个", removed);
    }

    @PreDestroy
    public void shutdown() {
        analystPool.shutdownNow();
    }

    // ===== 数据结构 =====

    public enum Status { PENDING, COLLECTING, ANALYZING, SYNTHESIZING, DONE, FAILED }

    @Data
    public static class BenchmarkTask {
        private String taskId;
        private String selfModel;
        private List<String> competitorModels;
        private Status status = Status.PENDING;
        private Long startedAt;
        private Long finishedAt;
        private String error;
        private BenchmarkContext context;
        /** dimension.name() → result */
        private Map<String, AnalystResult> analystResults = new LinkedHashMap<>();
        private String reportMarkdown;

        // 不参与 JSON 序列化（仅运行时）
        @com.fasterxml.jackson.annotation.JsonIgnore
        private final List<Consumer<StepEvent>> listeners = new CopyOnWriteArrayList<>();
        @com.fasterxml.jackson.annotation.JsonIgnore
        private final List<StepEvent> history = new CopyOnWriteArrayList<>();
    }

    @Data
    public static class StepEvent {
        private String step;       // DATA_COLLECTOR / POWER / BODY / OFFROAD / PRICE / SALES / SYNTHESIZER / TASK
        private String status;     // RUNNING / DONE / FAILED
        private Long ts;
        private String error;
        private Object payload;

        public static StepEvent of(String step, String status, String error, Object payload) {
            StepEvent e = new StepEvent();
            e.step = step;
            e.status = status;
            e.error = error;
            e.payload = payload;
            e.ts = System.currentTimeMillis();
            return e;
        }
    }
}
