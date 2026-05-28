package com.nevinsight.intelligence.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.config.RagflowProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;

/**
 * HTTP client for RAGFlow v0.25.x — supports retrieval, document upload/delete/list.
 *
 * RAGFlow Auth: header `Authorization: Bearer {api_key}`.
 *
 * Endpoints (RAGFlow HTTP API v1):
 *   POST   /api/v1/retrieval
 *   POST   /api/v1/datasets/{kb_id}/documents          (multipart: file)
 *   DELETE /api/v1/datasets/{kb_id}/documents          (json: {ids: [...]})
 *   GET    /api/v1/datasets/{kb_id}/documents?keywords={name}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagflowClient {

    private final RagflowProperties props;
    private final ObjectMapper objectMapper;
    private RestTemplate http;

    @PostConstruct
    void init() {
        this.http = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()))
                .build();
    }

    public boolean isConfigured() {
        return props.getApiKey() != null && !props.getApiKey().isEmpty()
                && props.getKbId() != null && !props.getKbId().isEmpty();
    }

    /** POST /api/v1/retrieval — 自然语言检索 KB 切片，返回 top-K 相关 chunk。 */
    public List<RetrievedChunk> retrieve(String question, int topK) {
        if (!isConfigured()) {
            log.warn("[Ragflow] not configured (missing api-key or kb-id); skip retrieve");
            return Collections.emptyList();
        }
        int effective = topK > 0 ? topK : props.getDefaultTopK();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        body.put("dataset_ids", Collections.singletonList(props.getKbId()));
        // RAGFlow v0.25：page_size 控最终返回数；top_k 仅控向量召回。两个都传以兜底。
        body.put("page_size", effective);
        body.put("top_k", Math.max(effective * 4, 64));
        body.put("similarity_threshold", props.getSimilarityThreshold());
        body.put("vector_similarity_weight", 1.0 - props.getKeywordWeight());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.postForObject(
                    props.getBaseUrl() + "/api/v1/retrieval",
                    new HttpEntity<>(body, jsonHeaders()),
                    Map.class);
            return parseChunks(resp);
        } catch (HttpStatusCodeException e) {
            log.error("[Ragflow] retrieve failed status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("[Ragflow] retrieve exception: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<RetrievedChunk> parseChunks(Map<String, Object> resp) {
        if (resp == null) return Collections.emptyList();
        // RAGFlow v0.25 response: { code: 0, data: { chunks: [{content, document_keyword, similarity, ...}] } }
        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map)) return Collections.emptyList();
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object chunksObj = data.get("chunks");
        if (!(chunksObj instanceof List)) return Collections.emptyList();

        List<RetrievedChunk> out = new ArrayList<>();
        for (Object c : (List<Object>) chunksObj) {
            if (!(c instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) c;
            String content = strVal(m, "content", "content_with_weight");
            String docName = strVal(m, "document_keyword", "docnm_kwd");
            double sim = doubleVal(m.get("similarity"));
            out.add(new RetrievedChunk(content, docName, sim));
        }
        return out;
    }

    /** POST /api/v1/datasets/{kb_id}/documents — multipart 上传文件。返回 document id（失败返 null）。 */
    public String uploadDocument(byte[] content, String filename) {
        if (!isConfigured()) {
            log.warn("[Ragflow] not configured; skip upload {}", filename);
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + props.getApiKey());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() { return filename; }
        };
        form.add("file", resource);

        String url = props.getBaseUrl() + "/api/v1/datasets/" + props.getKbId() + "/documents";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.postForObject(url,
                    new HttpEntity<>(form, headers), Map.class);
            return firstDocId(resp);
        } catch (HttpStatusCodeException e) {
            log.error("[Ragflow] upload failed name={} status={} body={}",
                    filename, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("[Ragflow] upload exception name={} err={}", filename, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String firstDocId(Map<String, Object> resp) {
        if (resp == null) return null;
        Object data = resp.get("data");
        if (data instanceof List && !((List<?>) data).isEmpty()) {
            Object first = ((List<?>) data).get(0);
            if (first instanceof Map) return strVal((Map<String, Object>) first, "id");
        } else if (data instanceof Map) {
            return strVal((Map<String, Object>) data, "id");
        }
        return null;
    }

    /** DELETE /api/v1/datasets/{kb_id}/documents — 删除指定 id 列表。 */
    public boolean deleteDocument(String docId) {
        if (!isConfigured() || docId == null || docId.isEmpty()) return false;
        HttpHeaders headers = jsonHeaders();
        Map<String, Object> body = Collections.singletonMap("ids", Collections.singletonList(docId));
        String url = props.getBaseUrl() + "/api/v1/datasets/" + props.getKbId() + "/documents";
        try {
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = http.exchange(url, HttpMethod.DELETE, req, Map.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (HttpStatusCodeException e) {
            log.error("[Ragflow] delete failed id={} status={} body={}",
                    docId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("[Ragflow] delete exception id={} err={}", docId, e.getMessage());
            return false;
        }
    }

    /** GET /api/v1/datasets/{kb_id}/documents?keywords={name} — 按文件名找 doc id（用于覆盖更新）。 */
    @SuppressWarnings("unchecked")
    public Optional<String> findDocIdByName(String filename) {
        if (!isConfigured() || filename == null || filename.isEmpty()) return Optional.empty();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + props.getApiKey());
        String url = props.getBaseUrl() + "/api/v1/datasets/" + props.getKbId()
                + "/documents?keywords=" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8);
        try {
            ResponseEntity<Map> resp = http.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) return Optional.empty();
            Object data = body.get("data");
            List<?> docs = null;
            if (data instanceof Map) docs = (List<?>) ((Map<String, Object>) data).get("docs");
            else if (data instanceof List) docs = (List<?>) data;
            if (docs == null) return Optional.empty();
            for (Object d : docs) {
                if (!(d instanceof Map)) continue;
                Map<String, Object> doc = (Map<String, Object>) d;
                String name = strVal(doc, "name");
                if (filename.equals(name)) return Optional.ofNullable(strVal(doc, "id"));
            }
            return Optional.empty();
        } catch (HttpStatusCodeException e) {
            log.error("[Ragflow] findDocIdByName failed name={} status={} body={}",
                    filename, e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("[Ragflow] findDocIdByName exception name={} err={}", filename, e.getMessage());
            return Optional.empty();
        }
    }

    /** 覆盖上传：若同名 doc 存在，先删后传。 */
    public String upsertDocument(byte[] content, String filename) {
        findDocIdByName(filename).ifPresent(id -> {
            log.info("[Ragflow] deleting existing doc name={} id={}", filename, id);
            deleteDocument(id);
        });
        return uploadDocument(content, filename);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + props.getApiKey());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static String strVal(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return String.valueOf(v);
        }
        return "";
    }

    private static double doubleVal(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }

    // ===== v9.6 Agent / Chat Assistant 调用 =====

    /**
     * 调用 RAGFlow Chat Assistant（单 LLM + KB 检索）。
     *
     * 流程：
     *   1. POST /api/v1/chats/{chat_id}/sessions  创建 session（每次调用都新建，简化无状态）
     *   2. POST /api/v1/chats/{chat_id}/completions  把问题发给 chat，拿 markdown 答复
     *
     * @return assistant 的 markdown 回答；失败返 null
     */
    public String chatCompletion(String chatId, String question) {
        if (!isConfigured()) return null;
        String sessionId = createChatSession(chatId, "auto-" + System.currentTimeMillis());
        if (sessionId == null) return null;
        return chatComplete(chatId, sessionId, question);
    }

    /** 创建 Chat session，返 session_id */
    public String createChatSession(String chatId, String name) {
        try {
            HttpHeaders headers = baseHeaders();
            Map<String, Object> body = Map.of("name", name);
            ResponseEntity<Map> resp = http.exchange(
                    props.getBaseUrl() + "/api/v1/chats/" + chatId + "/sessions",
                    HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
            return data == null ? null : String.valueOf(data.get("id"));
        } catch (Exception e) {
            log.error("[Ragflow] createChatSession 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * POST /api/v1/chats/{id}/completions — RAGFlow 永远 SSE 流，需收所有 chunk 拼接。
     * Request 必须用 messages 字段（不是 question），否则 400。
     */
    public String chatComplete(String chatId, String sessionId, String question) {
        try {
            String url = props.getBaseUrl() + "/api/v1/chats/" + chatId + "/completions";
            HttpHeaders headers = baseHeaders();
            Map<String, Object> body = new LinkedHashMap<>();
            // RAGFlow 期待 messages 数组
            body.put("messages", List.of(Map.of("role", "user", "content", question)));
            if (sessionId != null && !sessionId.isEmpty()) body.put("session_id", sessionId);
            body.put("stream", true);

            // 拿原始 SSE 流（String 形式），逐行 parse `data: {...}` 收 answer chunk
            ResponseEntity<String> resp = http.exchange(url,
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String raw = resp.getBody();
            if (raw == null || raw.isEmpty()) return null;

            // RAGFlow chunks 是 cumulative（实测前 chunk 含完整开头，后 chunk 是增量？需 inspect）
            // 实测：每 chunk 是 INCREMENTAL 不是 cumulative，拼接所有
            StringBuilder full = new StringBuilder();
            for (String line : raw.split("\n")) {
                line = line.trim();
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.equals("true") || payload.isEmpty()) continue;
                try {
                    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
                    com.fasterxml.jackson.databind.JsonNode data = node.get("data");
                    if (data == null || data.isNull() || !data.has("answer")) continue;
                    String chunk = data.get("answer").asText("");
                    if (!chunk.isEmpty()) full.append(chunk);
                } catch (Exception ignored) {}
            }
            String answer = full.toString();
            return answer.isEmpty() ? null : answer;
        } catch (Exception e) {
            log.error("[Ragflow] chatComplete 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 调用 RAGFlow Agent（workflow DAG）。
     *
     * 流程：
     *   1. POST /api/v1/agents/{agent_id}/sessions
     *   2. POST /api/v1/agents/{agent_id}/completions  传 question
     *
     * @param agentId Agent canvas id
     * @param question 用户输入（agent Begin 节点的 query 变量）
     * @return 末端 Message 节点输出的 markdown
     */
    public String agentCompletion(String agentId, String question) {
        if (!isConfigured()) return null;
        String sessionId = createAgentSession(agentId);
        if (sessionId == null) return null;
        return agentComplete(agentId, sessionId, question);
    }

    public String createAgentSession(String agentId) {
        try {
            HttpHeaders headers = baseHeaders();
            ResponseEntity<Map> resp = http.exchange(
                    props.getBaseUrl() + "/api/v1/agents/" + agentId + "/sessions",
                    HttpMethod.POST, new HttpEntity<>(Collections.emptyMap(), headers), Map.class);
            Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
            return data == null ? null : String.valueOf(data.get("id"));
        } catch (Exception e) {
            log.error("[Ragflow] createAgentSession 失败: {}", e.getMessage());
            return null;
        }
    }

    public String agentComplete(String agentId, String sessionId, String question) {
        try {
            HttpHeaders headers = baseHeaders();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("question", question);
            body.put("stream", false);
            body.put("session_id", sessionId);
            ResponseEntity<Map> resp = http.exchange(
                    props.getBaseUrl() + "/api/v1/agents/" + agentId + "/completions",
                    HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
            if (data == null) return null;
            Object answer = data.get("answer");
            return answer == null ? null : String.valueOf(answer);
        } catch (Exception e) {
            log.error("[Ragflow] agentComplete 失败: {}", e.getMessage());
            return null;
        }
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + props.getApiKey());
        return headers;
    }

    /** 一个检索到的 KB 切片。 */
    public static class RetrievedChunk {
        public final String content;
        public final String docName;
        public final double similarity;

        public RetrievedChunk(String content, String docName, double similarity) {
            this.content = content;
            this.docName = docName;
            this.similarity = similarity;
        }

        @Override
        public String toString() {
            return "[" + docName + " sim=" + String.format("%.2f", similarity) + "] " + content;
        }
    }
}
