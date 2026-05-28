package com.nevinsight.admin.controller.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.intelligence.agent.FollowupAgent;
import com.nevinsight.intelligence.client.RagflowClient;
import com.nevinsight.intelligence.config.RagflowProperties;
import com.nevinsight.intelligence.dto.AnalystResult;
import com.nevinsight.intelligence.service.ChatSessionService;
import com.nevinsight.intelligence.service.DeepBenchmarkService;
import com.nevinsight.intelligence.service.DeepBenchmarkService.BenchmarkTask;
import com.nevinsight.model.entity.core.DeepBenchmarkMessage;
import com.nevinsight.model.entity.core.DeepBenchmarkSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * v9.5 对话式深度对标 REST。
 *
 * 端点：
 *   POST /chat/start         首次对标（指定本品 + 竞品）→ 启动 5-analyst + 创建 session + 异步等结果
 *   POST /chat/followup       追问（在已有 session 上）
 *   GET  /chat/sessions       列当前用户历史会话
 *   GET  /chat/session/{id}   完整会话（含所有消息）
 *
 * 用户隔离：v9.5 用 X-User-Token header（前端 localStorage UUID）；v9.6 改飞书 user_id。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatBenchmarkController {

    private final ChatSessionService chatService;
    private final DeepBenchmarkService benchmarkService;
    private final FollowupAgent followupAgent;
    private final RagflowClient ragflowClient;
    private final RagflowProperties ragflowProperties;
    private final ObjectMapper objectMapper;

    // ===== 列历史会话 =====

    @GetMapping("/sessions")
    public ApiResponse<List<DeepBenchmarkSession>> listSessions(
            @RequestHeader(value = "X-User-Token", required = false, defaultValue = "anonymous") String userToken,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.success(chatService.listSessions(userToken, limit));
    }

    // ===== 取单个会话（含所有消息）=====

    @GetMapping("/session/{id}")
    public ApiResponse<Map<String, Object>> getSession(@PathVariable Long id) {
        DeepBenchmarkSession s = chatService.getSession(id);
        if (s == null) return ApiResponse.error(404, "会话不存在");
        List<DeepBenchmarkMessage> msgs = chatService.getMessages(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", s);
        data.put("messages", msgs);
        return ApiResponse.success(data);
    }

    // ===== 首次对标：创建会话 + 启动 5-analyst + 同步等结果（写入 message）=====

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(
            @RequestHeader(value = "X-User-Token", required = false, defaultValue = "anonymous") String userToken,
            @RequestParam String selfModel,
            @RequestParam String competitorModels,
            @RequestParam(required = false) String userPrompt) {
        List<String> competitors = new ArrayList<>();
        for (String s : competitorModels.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) competitors.add(t);
        }
        if (competitors.isEmpty()) return ApiResponse.error(400, "至少选 1 个竞品");

        // 1. 创建 session
        String firstUserMsg = userPrompt != null && !userPrompt.isEmpty()
                ? userPrompt
                : String.format("帮我对比 %s 和 %s", selfModel, String.join(" / ", competitors));
        DeepBenchmarkSession session = chatService.createSession(userToken, selfModel, competitors, firstUserMsg);

        // 2. 写第一条 user message
        chatService.appendMessage(session.getId(), "user", firstUserMsg, null);

        // 3. 路由：优先 RAGFlow Agent / Chat Assistant；未配置则 fallback Java
        String ragflowAgentId = ragflowProperties.getDeepBenchmarkAgentId();
        String ragflowChatId = ragflowProperties.getDeepBenchmarkChatId();
        String markdown = null;
        String engineLabel = "java";
        long start = System.currentTimeMillis();
        Map<String, Object> metadata = new LinkedHashMap<>();
        String taskId = null;
        BenchmarkTask task = null;
        try {
            if (ragflowAgentId != null && !ragflowAgentId.isEmpty()) {
                // 路径 A：RAGFlow Agent workflow
                String question = String.format(
                        "请对比本品 %s 与竞品 %s。本品级别参考 autohome_spec。" +
                        "用法：先调 GET /api/v1/benchmark/data?selfModel=%s&competitorModels=%s&dimension=power 等 5 维度，" +
                        "汇总输出图文 Markdown 报告。用户诉求：%s",
                        selfModel, String.join(",", competitors),
                        selfModel, String.join(",", competitors),
                        firstUserMsg);
                markdown = ragflowClient.agentCompletion(ragflowAgentId, question);
                engineLabel = "ragflow-agent";
            } else if (ragflowChatId != null && !ragflowChatId.isEmpty()) {
                // 路径 B：RAGFlow Chat Assistant（单 LLM + KB）
                String question = String.format(
                        "请对比本品 %s 与竞品 %s 做深度对标分析，输出 Markdown 报告（含 mermaid radar / chart 图表）。用户诉求：%s",
                        selfModel, String.join(",", competitors), firstUserMsg);
                markdown = ragflowClient.chatCompletion(ragflowChatId, question);
                engineLabel = "ragflow-chat";
            }
        } catch (Exception e) {
            log.error("[Chat] RAGFlow 调用失败 fallback to Java: {}", e.getMessage());
        }

        // 路径 C（fallback）：Java 5-analyst 编排
        if (markdown == null || markdown.isEmpty()) {
            engineLabel = "java"; // RAGFlow 失败，重置标签
            taskId = benchmarkService.startAsync(selfModel, competitors);
            long deadline = System.currentTimeMillis() + 240_000L;
            while (true) {
                task = benchmarkService.getTask(taskId);
                if (task == null) break;
                if (task.getStatus() == DeepBenchmarkService.Status.DONE
                        || task.getStatus() == DeepBenchmarkService.Status.FAILED) break;
                if (System.currentTimeMillis() > deadline) break;
                try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (task == null || task.getStatus() != DeepBenchmarkService.Status.DONE) {
                String err = task == null ? "任务丢失" : (task.getError() == null ? "超时" : task.getError());
                chatService.appendMessage(session.getId(), "assistant",
                        "⚠ 深度对标失败：" + err, Map.of("agentType", "synthesizer", "error", err));
                return ApiResponse.error(500, "深度对标失败：" + err);
            }
            markdown = task.getReportMarkdown();
            metadata.put("analystResults", task.getAnalystResults());
        }

        // 4. 写 assistant message
        metadata.put("agentType", "synthesizer");
        metadata.put("engine", engineLabel);
        if (taskId != null) metadata.put("taskId", taskId);
        metadata.put("elapsedMs", System.currentTimeMillis() - start);
        DeepBenchmarkMessage assistantMsg = chatService.appendMessage(
                session.getId(), "assistant", markdown, metadata);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", session);
        data.put("userMessage", chatService.getMessages(session.getId()).get(0));
        data.put("assistantMessage", assistantMsg);
        return ApiResponse.success(data);
    }

    // ===== 追问：基于已有 session 的 followup =====

    @PostMapping("/followup")
    public ApiResponse<Map<String, Object>> followup(
            @RequestParam Long sessionId,
            @RequestParam String userPrompt) {
        DeepBenchmarkSession session = chatService.getSession(sessionId);
        if (session == null) return ApiResponse.error(404, "会话不存在");
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return ApiResponse.error(400, "userPrompt 不能为空");
        }

        // 1. 写 user message
        DeepBenchmarkMessage userMsg = chatService.appendMessage(sessionId, "user", userPrompt.trim(), null);

        // 2. 加载历史 → 路由（优先 RAGFlow Chat）
        List<DeepBenchmarkMessage> history = chatService.getMessages(sessionId);
        history.removeIf(m -> m.getId().equals(userMsg.getId()));

        String followupChatId = ragflowProperties.getFollowupChatId();
        if (followupChatId == null || followupChatId.isEmpty()) {
            // 复用 deep-benchmark chat id 做追问
            followupChatId = ragflowProperties.getDeepBenchmarkChatId();
        }

        String answer = null;
        String engineLabel = "java";
        if (followupChatId != null && !followupChatId.isEmpty()) {
            // 拼历史为单个 question（RAGFlow Chat 的 session 在我们这边是一次性，所以历史拼到 question 里）
            StringBuilder qb = new StringBuilder();
            qb.append("【对标场景】本品=").append(session.getSelfModel())
              .append(" 竞品=").append(session.getCompetitorModels()).append("\n\n");
            qb.append("【历史对话摘要】\n");
            for (DeepBenchmarkMessage m : history) {
                String c = m.getContent() == null ? "" : m.getContent();
                if (c.length() > 1500) c = c.substring(0, 1500) + "…";
                qb.append("[").append(m.getRole()).append("] ").append(c).append("\n\n");
            }
            qb.append("【新追问】\n").append(userPrompt.trim());
            try {
                answer = ragflowClient.chatCompletion(followupChatId, qb.toString());
                engineLabel = "ragflow-chat";
            } catch (Exception e) {
                log.warn("[Chat] RAGFlow followup 失败 fallback: {}", e.getMessage());
            }
        }
        if (answer == null || answer.isEmpty()) {
            engineLabel = "java"; // RAGFlow 失败，重置
            answer = followupAgent.answer(session, history, userPrompt.trim());
        }

        // 3. 写 assistant message
        Map<String, Object> metadata = Map.of("agentType", "followup", "engine", engineLabel);
        DeepBenchmarkMessage assistantMsg = chatService.appendMessage(sessionId, "assistant", answer, metadata);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userMessage", userMsg);
        data.put("assistantMessage", assistantMsg);
        return ApiResponse.success(data);
    }
}
