package com.nevinsight.intelligence.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.config.DashScopeProperties;
import com.nevinsight.model.entity.core.LlmAuditLog;
import com.nevinsight.model.mapper.core.LlmAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QwenAiService {

    private final DashScopeProperties dashScopeProperties;
    private final LlmAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private RestTemplate http;
    private static final String COMPATIBLE_ENDPOINT =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    @PostConstruct
    void initHttp() {
        // 用足够长的 read timeout（qwen-max 10K prompt 可能 60-120 秒）；OpenAI 兼容模式
        // 不像 SDK 那样依赖长 idle 连接，被中间设备切断概率低很多。
        this.http = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(300))
                .build();
    }

    /**
     * 调用通义千问
     *
     * @param model       模型名 (qwen-max / qwen-turbo)
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM 生成的文本
     */
    @Retryable(value = {Exception.class}, maxAttempts = 2,
            backoff = @Backoff(delay = 1500, multiplier = 1.5))
    public String chat(String model, String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMsg = null;
        Integer inputTokens = null;
        Integer outputTokens = null;

        try {
            Generation gen = new Generation();
            Message systemMsg = Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(systemPrompt)
                    .build();
            Message userMsg = Message.builder()
                    .role(Role.USER.getValue())
                    .content(userPrompt)
                    .build();

            GenerationParam param = GenerationParam.builder()
                    .model(model)
                    .messages(Arrays.asList(systemMsg, userMsg))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .apiKey(dashScopeProperties.getApiKey())
                    .build();

            GenerationResult result = gen.call(param);

            inputTokens = result.getUsage().getInputTokens();
            outputTokens = result.getUsage().getOutputTokens();

            return result.getOutput().getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            log.error("[QwenAI] 调用失败 model={}: {}", model, e.getMessage());
            throw new RuntimeException("LLM调用异常: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            saveAuditLog("UNKNOWN", model, inputTokens, outputTokens, (int) latencyMs, status, errorMsg);
        }
    }

    /**
     * 使用报告模型 (qwen-max) 生成
     */
    public String chatWithReportModel(String systemPrompt, String userPrompt) {
        return chat(dashScopeProperties.getReportModel(), systemPrompt, userPrompt);
    }

    /**
     * 走 OpenAI 兼容 HTTP 端点（避开 DashScope SDK 在容器 docker network 下的长连接断流问题）。
     * 用于竞品分析 Agent，prompt 大、响应慢，需要稳定的短超时 + 单次明确反馈。
     */
    @Retryable(value = {Exception.class}, maxAttempts = 2,
            backoff = @Backoff(delay = 1000, multiplier = 1.5))
    public String chatViaHttp(String model, String systemPrompt, String userPrompt) {
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMsg = null;
        Integer inputTokens = null;
        Integer outputTokens = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            List<Map<String, String>> msgs = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                msgs.add(Map.of("role", "system", "content", systemPrompt));
            }
            msgs.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", msgs);
            // 不传 response_format — qwen-max 对 json_object 支持不稳；改由 prompt 要求 JSON。

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + dashScopeProperties.getApiKey());

            log.info("[QwenAI-HTTP] POST {} model={} prompt={} chars",
                    COMPATIBLE_ENDPOINT, model, userPrompt.length());
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.postForObject(COMPATIBLE_ENDPOINT,
                    new HttpEntity<>(body, headers), Map.class);
            log.info("[QwenAI-HTTP] response received");
            if (resp == null) throw new RuntimeException("empty response");

            Map<String, Object> usage = (Map<String, Object>) resp.get("usage");
            if (usage != null) {
                Object in = usage.get("prompt_tokens");
                Object out = usage.get("completion_tokens");
                if (in instanceof Number) inputTokens = ((Number) in).intValue();
                if (out instanceof Number) outputTokens = ((Number) out).intValue();
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) throw new RuntimeException("no choices in response");
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return msg == null ? "" : String.valueOf(msg.get("content"));
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            log.error("[QwenAI-HTTP] 调用失败 model={}: {}", model, e.getMessage());
            throw new RuntimeException("LLM调用异常: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            saveAuditLog("UNKNOWN", model, inputTokens, outputTokens, (int) latencyMs, status, errorMsg);
        }
    }

    /** 报告模型走 HTTP（用于竞品分析）。 */
    public String chatWithReportModelHttp(String systemPrompt, String userPrompt) {
        return chatViaHttp(dashScopeProperties.getReportModel(), systemPrompt, userPrompt);
    }

    /**
     * v8: 多模态（视觉）模型 HTTP 调用 — 用于官号海报 OCR + 政策提取。
     * messages[user].content 走 OpenAI 兼容格式：[{type:"text"},{type:"image_url"},...]
     */
    @Retryable(value = {Exception.class}, maxAttempts = 2,
            backoff = @Backoff(delay = 1000, multiplier = 1.5))
    public String chatWithVisionModelHttp(String systemPrompt, String userText, List<String> imageUrls) {
        long startTime = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMsg = null;
        Integer inputTokens = null;
        Integer outputTokens = null;
        String model = dashScopeProperties.getVisionModel();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);

            List<Map<String, Object>> msgs = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                msgs.add(Map.of("role", "system", "content", systemPrompt));
            }

            // user 消息 content 是数组：先文字，再每张图
            List<Map<String, Object>> userContent = new ArrayList<>();
            userContent.add(Map.of("type", "text", "text", userText == null ? "" : userText));
            if (imageUrls != null) {
                for (String url : imageUrls) {
                    if (url == null || url.isEmpty()) continue;
                    Map<String, Object> imageBlock = new LinkedHashMap<>();
                    imageBlock.put("type", "image_url");
                    imageBlock.put("image_url", Map.of("url", url));
                    userContent.add(imageBlock);
                }
            }
            Map<String, Object> userMsg = new LinkedHashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userContent);
            msgs.add(userMsg);

            body.put("messages", msgs);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + dashScopeProperties.getApiKey());

            int imgCount = imageUrls == null ? 0 : (int) imageUrls.stream().filter(u -> u != null && !u.isEmpty()).count();
            log.info("[QwenAI-VL] POST {} model={} text={} chars images={}",
                    COMPATIBLE_ENDPOINT, model, userText == null ? 0 : userText.length(), imgCount);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.postForObject(COMPATIBLE_ENDPOINT,
                    new HttpEntity<>(body, headers), Map.class);
            if (resp == null) throw new RuntimeException("empty response");

            @SuppressWarnings("unchecked")
            Map<String, Object> usage = (Map<String, Object>) resp.get("usage");
            if (usage != null) {
                Object in = usage.get("prompt_tokens");
                Object out = usage.get("completion_tokens");
                if (in instanceof Number) inputTokens = ((Number) in).intValue();
                if (out instanceof Number) outputTokens = ((Number) out).intValue();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) throw new RuntimeException("no choices in response");
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null) return "";
            // vision 模型返回可能是字符串或 list；统一转字符串
            Object content = msg.get("content");
            if (content instanceof String) return (String) content;
            if (content instanceof List) {
                StringBuilder sb = new StringBuilder();
                for (Object item : (List<?>) content) {
                    if (item instanceof Map) {
                        Object t = ((Map<?, ?>) item).get("text");
                        if (t != null) sb.append(t);
                    }
                }
                return sb.toString();
            }
            return content == null ? "" : String.valueOf(content);
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            log.error("[QwenAI-VL] 调用失败 model={}: {}", model, e.getMessage());
            throw new RuntimeException("LLM Vision 调用异常: " + e.getMessage(), e);
        } finally {
            long latencyMs = System.currentTimeMillis() - startTime;
            saveAuditLog("VISION", model, inputTokens, outputTokens, (int) latencyMs, status, errorMsg);
        }
    }

    /** 筛选模型 qwen-turbo 走 HTTP（用于事件分类等大批量轻量任务）。 */
    public String chatWithScreeningModelHttp(String systemPrompt, String userPrompt) {
        return chatViaHttp(dashScopeProperties.getScreeningModel(), systemPrompt, userPrompt);
    }

    /**
     * 使用筛选模型 (qwen-turbo) 生成
     */
    public String chatWithScreeningModel(String systemPrompt, String userPrompt) {
        return chat(dashScopeProperties.getScreeningModel(), systemPrompt, userPrompt);
    }

    private void saveAuditLog(String agentName, String model, Integer inputTokens,
                              Integer outputTokens, int latencyMs, String status, String errorMsg) {
        try {
            LlmAuditLog auditLog = new LlmAuditLog();
            auditLog.setAgentName(agentName);
            auditLog.setModelName(model);
            auditLog.setInputTokens(inputTokens);
            auditLog.setOutputTokens(outputTokens);
            auditLog.setLatencyMs(latencyMs);
            auditLog.setStatus(status);
            auditLog.setErrorMessage(errorMsg);
            auditLog.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("[QwenAI] 审计日志保存失败: {}", e.getMessage());
        }
    }
}
