package com.nevinsight.report.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class FeishuPushService {

    private static final String TENANT_TOKEN_URL =
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String SEND_MESSAGE_URL =
            "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";

    @Value("${nevinsight.feishu.test-webhook-url:}")
    private String testWebhookUrl;

    @Value("${nevinsight.feishu.prod-webhook-url:}")
    private String prodWebhookUrl;

    @Value("${nevinsight.feishu.app-id:}")
    private String appId;

    @Value("${nevinsight.feishu.app-secret:}")
    private String appSecret;

    @Value("${nevinsight.feishu.competitor-chat-id:}")
    private String competitorChatId;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public FeishuPushService(ObjectMapper objectMapper) {
        this(objectMapper, new RestTemplate());
    }

    FeishuPushService(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * 推送卡片到飞书
     */
    public boolean sendCard(String cardJson, String webhookUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(cardJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl, HttpMethod.POST, entity, String.class);
            String body = response.getBody() == null ? "" : response.getBody();
            // 飞书 webhook 即使卡片被拒也会返回 HTTP 200，需读 body.code 才能判断
            boolean success = response.getStatusCode() == HttpStatus.OK
                    && body.contains("\"code\":0");
            if (success) {
                log.info("[Feishu] 推送成功: {}", webhookUrl);
            } else {
                log.error("[Feishu] 推送失败 status={} body={}", response.getStatusCode(), body);
            }
            return success;
        } catch (Exception e) {
            log.error("[Feishu] 推送异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 使用飞书企业自建应用机器人发送竞品日报卡片。
     * 发送接口需要 tenant_access_token；卡片 content 是 card 对象序列化后的 JSON 字符串。
     */
    public boolean sendCompetitorCardByAppBot(String cardJson) {
        return sendCardByAppBot(cardJson, competitorChatId);
    }

    public boolean sendCardByAppBot(String cardJson, String chatId) {
        if (isBlank(appId) || isBlank(appSecret) || isBlank(chatId)) {
            log.warn("[FeishuAppBot] app-id/app-secret/chat-id not configured; skip push");
            return false;
        }
        try {
            String token = getTenantAccessToken();
            if (isBlank(token)) {
                return false;
            }

            JsonNode card = extractCard(cardJson);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receive_id", chatId.trim());
            body.put("msg_type", "interactive");
            body.put("content", objectMapper.writeValueAsString(card));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            ResponseEntity<String> response = restTemplate.exchange(
                    SEND_MESSAGE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
            boolean success = response.getStatusCode() == HttpStatus.OK && root.path("code").asInt(-1) == 0;
            if (success) {
                log.info("[FeishuAppBot] 推送成功 chatId={} messageId={}",
                        chatId, root.path("data").path("message_id").asText(""));
            } else {
                log.error("[FeishuAppBot] 推送失败 status={} body={}", response.getStatusCode(), response.getBody());
            }
            return success;
        } catch (Exception e) {
            log.error("[FeishuAppBot] 推送异常: {}", e.getMessage());
            return false;
        }
    }

    private String getTenantAccessToken() throws Exception {
        Map<String, String> body = Map.of("app_id", appId.trim(), "app_secret", appSecret.trim());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                TENANT_TOKEN_URL,
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                String.class);
        JsonNode root = objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        if (response.getStatusCode() != HttpStatus.OK || root.path("code").asInt(-1) != 0) {
            log.error("[FeishuAppBot] tenant_access_token 获取失败 status={} body={}",
                    response.getStatusCode(), response.getBody());
            return null;
        }
        return root.path("tenant_access_token").asText("");
    }

    private JsonNode extractCard(String cardJson) throws Exception {
        JsonNode root = objectMapper.readTree(cardJson);
        JsonNode card = root.path("card");
        if (card.isMissingNode() || card.isNull()) {
            throw new IllegalArgumentException("cardJson missing card node");
        }
        return card;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean sendToTest(String cardJson) {
        return sendCard(cardJson, testWebhookUrl);
    }

    public boolean sendToProduction(String cardJson) {
        return sendCard(cardJson, prodWebhookUrl);
    }
}
