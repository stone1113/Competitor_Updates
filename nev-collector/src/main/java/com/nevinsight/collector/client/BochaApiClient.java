package com.nevinsight.collector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Component
public class BochaApiClient {

    @Value("${nevinsight.bocha.api-key:}")
    private String apiKey;

    @Value("${nevinsight.bocha.base-url:https://api.bocha.cn/v1/ai-search}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 搜索最近24小时新闻（兼容 BochaAI ai-search API 格式）
     *
     * 请求格式: POST {query, freshness, stream, answer}
     * 响应格式: {code: 200, messages: [{role, type, content_type, content}]}
     */
    public List<Map<String, Object>> searchLast24h(String query, int count) {
        return search(query, count, "oneDay");
    }

    public List<Map<String, Object>> searchLastWeek(String query, int count) {
        return search(query, count, "oneWeek");
    }

    public List<Map<String, Object>> search(String query, int count, String freshness) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[BochaAPI] API Key 未配置，跳过搜索");
            return Collections.emptyList();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Accept", "*/*");

            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("freshness", (freshness == null || freshness.isEmpty()) ? "oneDay" : freshness);
            body.put("stream", false);
            body.put("answer", true);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());

                // 检查 API 响应码
                int code = root.path("code").asInt(0);
                if (code != 200) {
                    log.warn("[BochaAPI] API 返回错误: code={}, msg={}", code, root.path("msg").asText());
                    return Collections.emptyList();
                }

                // 解析 messages 中的网页结果
                List<Map<String, Object>> items = new ArrayList<>();
                JsonNode messages = root.path("messages");
                if (messages.isArray()) {
                    for (JsonNode msg : messages) {
                        if (!"assistant".equals(msg.path("role").asText())) continue;
                        String contentType = msg.path("content_type").asText("");

                        // 网页搜索结果在 content_type=webpage 的 message 中
                        // content 是 JSON 字符串: {"webSearchUrl":"...", "value":[{name, url, snippet, ...}]}
                        if ("webpage".equals(contentType)) {
                            String contentStr = msg.path("content").asText("{}");
                            JsonNode contentObj = objectMapper.readTree(contentStr);
                            JsonNode valueArray = contentObj.path("value");
                            if (valueArray.isArray()) {
                                for (JsonNode page : valueArray) {
                                    String pageUrl = page.path("url").asText("");
                                    if (pageUrl.isEmpty()) continue;
                                    Map<String, Object> item = new HashMap<>();
                                    item.put("title", truncate(page.path("name").asText(""), 500));
                                    item.put("url", truncate(pageUrl, 1024));
                                    item.put("content", truncate(page.path("snippet").asText(""), 2000));
                                    item.put("publishedDate", "");
                                    item.put("urlHash", sha256(pageUrl));
                                    items.add(item);
                                }
                            }
                        }
                    }
                }

                log.info("[BochaAPI] 搜索 '{}' 返回 {} 条结果", query, items.size());
                return items;
            }
        } catch (Exception e) {
            log.error("[BochaAPI] 搜索失败 query={}: {}", query, e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("403")) {
                log.warn("[BochaAPI] 403 权限错误，可能 Key 无效");
            }
        }
        return Collections.emptyList();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input.hashCode() + "";
        }
    }
}
