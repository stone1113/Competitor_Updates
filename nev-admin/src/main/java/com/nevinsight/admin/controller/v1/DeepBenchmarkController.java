package com.nevinsight.admin.controller.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.intelligence.service.DeepBenchmarkService;
import com.nevinsight.intelligence.service.DeepBenchmarkService.BenchmarkTask;
import com.nevinsight.intelligence.service.DeepBenchmarkService.StepEvent;
import com.nevinsight.report.feishu.FeishuPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * v9 深度对标 REST 端点。
 *
 * 流程：
 *   POST /start                → 返 {taskId}，异步跑
 *   GET  /{taskId}/stream      → SSE 事件流
 *   GET  /{taskId}             → 完整结果（用于刷新页面 / 重连场景）
 *   POST /{taskId}/push-feishu → 把 Markdown 报告推到飞书
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/deep-benchmark")
@RequiredArgsConstructor
public class DeepBenchmarkController {

    private final DeepBenchmarkService benchmarkService;
    private final FeishuPushService feishuPushService;
    private final ObjectMapper objectMapper;

    @Value("${nevinsight.feishu.competitor-webhook-url:}")
    private String competitorWebhook;

    /**
     * 启动任务。
     * @param selfModel 本品车型名（如「猛士M817」）
     * @param competitorModels 逗号分隔竞品车型名（如「方程豹豹5,问界M8 REEV」）
     */
    @PostMapping("/start")
    public ApiResponse<Map<String, String>> start(
            @RequestParam String selfModel,
            @RequestParam String competitorModels) {
        if (selfModel == null || selfModel.isEmpty()) {
            return ApiResponse.error(400, "selfModel 不能为空");
        }
        List<String> competitors = new ArrayList<>();
        for (String s : competitorModels.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) competitors.add(t);
        }
        if (competitors.isEmpty()) {
            return ApiResponse.error(400, "至少选 1 个竞品");
        }
        try {
            String taskId = benchmarkService.startAsync(selfModel, competitors);
            return ApiResponse.success(Map.of("taskId", taskId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[DeepBenchmarkController] 启动失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "启动失败: " + e.getMessage());
        }
    }

    /** SSE 流：实时推每 step 完成事件。 */
    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String taskId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        Consumer<StepEvent> listener = event -> {
            try {
                String json = objectMapper.writeValueAsString(event);
                emitter.send(SseEmitter.event()
                        .name(event.getStep())
                        .data(json, MediaType.APPLICATION_JSON));
                // 任务终态：close
                if ("TASK".equals(event.getStep()) &&
                        ("DONE".equals(event.getStatus()) || "FAILED".equals(event.getStatus()))) {
                    emitter.complete();
                }
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        };

        emitter.onCompletion(() -> benchmarkService.unsubscribe(taskId, listener));
        emitter.onTimeout(() -> benchmarkService.unsubscribe(taskId, listener));
        emitter.onError(e -> benchmarkService.unsubscribe(taskId, listener));

        benchmarkService.subscribe(taskId, listener);
        return emitter;
    }

    /** 查完整结果（重连 / 刷新场景） */
    @GetMapping("/{taskId}")
    public ApiResponse<BenchmarkTask> get(@PathVariable String taskId) {
        BenchmarkTask task = benchmarkService.getTask(taskId);
        if (task == null) return ApiResponse.error(404, "任务不存在或已过期");
        return ApiResponse.success(task);
    }

    /** 推飞书（Markdown 报告包成飞书卡片）。 */
    @PostMapping("/{taskId}/push-feishu")
    public ApiResponse<Map<String, Object>> pushFeishu(@PathVariable String taskId) {
        BenchmarkTask task = benchmarkService.getTask(taskId);
        if (task == null) return ApiResponse.error(404, "任务不存在或已过期");
        if (task.getReportMarkdown() == null || task.getReportMarkdown().isEmpty()) {
            return ApiResponse.error(400, "报告未生成完成，无法推送");
        }
        if (competitorWebhook == null || competitorWebhook.isEmpty()) {
            return ApiResponse.error(500, "FEISHU_COMPETITOR_WEBHOOK_URL 未配置");
        }

        try {
            String cardJson = buildReportCard(task);
            boolean ok = feishuPushService.sendCard(cardJson, competitorWebhook);
            return ApiResponse.success(Map.of("pushed", ok));
        } catch (Exception e) {
            log.error("[DeepBenchmarkController] 推飞书失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "推飞书失败: " + e.getMessage());
        }
    }

    /** 把 Markdown 报告包成飞书卡片 v2（schema 2.0）。 */
    private String buildReportCard(BenchmarkTask task) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("msg_type", "interactive");

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");
        card.put("config", Collections.singletonMap("width_mode", "fill"));

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("template", "blue");
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("tag", "plain_text");
        String competitorStr = String.join(" / ", task.getCompetitorModels());
        title.put("content", String.format("🤖 深度对标 · %s vs %s",
                task.getSelfModel(), competitorStr));
        header.put("title", title);
        card.put("header", header);

        // 直接把 Markdown 喂进去（lark_md 支持大部分语法）
        Map<String, Object> divNode = new LinkedHashMap<>();
        divNode.put("tag", "div");
        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("tag", "lark_md");
        // 截断到 < 10000 字符防超限
        String md = task.getReportMarkdown();
        if (md.length() > 9500) md = md.substring(0, 9500) + "\n\n… (报告过长已截断，完整版见后台) …";
        textNode.put("content", md);
        divNode.put("text", textNode);

        List<Object> elements = new ArrayList<>();
        elements.add(divNode);

        // footer note
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("tag", "div");
        Map<String, Object> noteText = new LinkedHashMap<>();
        noteText.put("tag", "lark_md");
        noteText.put("content", String.format(
                "<font color='grey'><i>NEV-Insight 深度对标智能体 · %s</i></font>",
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))));
        note.put("text", noteText);
        elements.add(note);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("direction", "vertical");
        body.put("elements", elements);
        card.put("body", body);
        root.put("card", card);

        return objectMapper.writeValueAsString(root);
    }
}
