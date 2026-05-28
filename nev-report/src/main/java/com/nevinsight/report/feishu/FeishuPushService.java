package com.nevinsight.report.feishu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class FeishuPushService {

    @Value("${nevinsight.feishu.test-webhook-url:}")
    private String testWebhookUrl;

    @Value("${nevinsight.feishu.prod-webhook-url:}")
    private String prodWebhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

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

    public boolean sendToTest(String cardJson) {
        return sendCard(cardJson, testWebhookUrl);
    }

    public boolean sendToProduction(String cardJson) {
        return sendCard(cardJson, prodWebhookUrl);
    }
}
