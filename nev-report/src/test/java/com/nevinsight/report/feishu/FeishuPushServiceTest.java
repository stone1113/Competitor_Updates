package com.nevinsight.report.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FeishuPushServiceTest {

    private static final String TOKEN_URL =
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String MESSAGE_URL =
            "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendCardByAppBot_gets_tenant_token_and_sends_interactive_card() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        FeishuPushService service = newService(restTemplate, "cli_1", "secret_1", "oc_1");

        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"app_id\":\"cli_1\",\"app_secret\":\"secret_1\"}"))
                .andRespond(withSuccess("{\"code\":0,\"tenant_access_token\":\"tenant-token\"}",
                        MediaType.APPLICATION_JSON));

        String cardJson = "{\"msg_type\":\"interactive\",\"card\":{\"schema\":\"2.0\",\"body\":{\"elements\":[]}}}";
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("receive_id", "oc_1");
        expected.put("msg_type", "interactive");
        expected.put("content", objectMapper.writeValueAsString(
                objectMapper.readTree(cardJson).path("card")));

        server.expect(requestTo(MESSAGE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tenant-token"))
                .andExpect(content().json(objectMapper.writeValueAsString(expected)))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"message_id\":\"om_1\"}}",
                        MediaType.APPLICATION_JSON));

        boolean sent = service.sendCompetitorCardByAppBot(cardJson);

        assertThat(sent).isTrue();
        server.verify();
    }

    @Test
    void sendCardByAppBot_returns_false_when_required_config_missing() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        FeishuPushService service = newService(restTemplate, "cli_1", "", "oc_1");

        boolean sent = service.sendCompetitorCardByAppBot("{}");

        assertThat(sent).isFalse();
        server.verify();
    }

    @Test
    void sendCardByAppBot_returns_false_when_token_request_fails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        FeishuPushService service = newService(restTemplate, "cli_1", "secret_1", "oc_1");

        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":999,\"msg\":\"bad app\"}",
                        MediaType.APPLICATION_JSON));

        boolean sent = service.sendCompetitorCardByAppBot("{}");

        assertThat(sent).isFalse();
        server.verify();
    }

    private FeishuPushService newService(RestTemplate restTemplate,
                                         String appId,
                                         String appSecret,
                                         String chatId) {
        FeishuPushService service = new FeishuPushService(objectMapper, restTemplate);
        ReflectionTestUtils.setField(service, "appId", appId);
        ReflectionTestUtils.setField(service, "appSecret", appSecret);
        ReflectionTestUtils.setField(service, "competitorChatId", chatId);
        return service;
    }
}
