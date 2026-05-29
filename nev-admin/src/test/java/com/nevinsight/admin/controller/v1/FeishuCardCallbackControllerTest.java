package com.nevinsight.admin.controller.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.admin.service.DailyReportFeedbackService;
import com.nevinsight.admin.service.DailyReportFeedbackService.FeedbackCommand;
import com.nevinsight.admin.service.DailyReportFeedbackService.RecordResult;
import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FeishuCardCallbackControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DailyReportFeedbackService feedbackService;
    private FeishuCardCallbackController controller;

    @BeforeEach
    void setUp() {
        feedbackService = mock(DailyReportFeedbackService.class);
        controller = new FeishuCardCallbackController(feedbackService);
        ReflectionTestUtils.setField(controller, "verificationToken", "token-1");
    }

    @Test
    void callback_records_feedback_and_starts_async_write() throws Exception {
        DailyReportFeedbackEvent event = new DailyReportFeedbackEvent();
        event.setId(12L);
        event.setEventId("evt-1");
        when(feedbackService.record(any())).thenReturn(new RecordResult(event, true));

        ResponseEntity<Map<String, Object>> response = controller.callback(readTree(
                "{"
                        + "\"header\":{\"event_id\":\"evt-1\",\"token\":\"token-1\",\"event_type\":\"card.action.trigger\"},"
                        + "\"event\":{"
                        + "\"operator\":{\"open_id\":\"ou_1\",\"union_id\":\"on_1\"},"
                        + "\"context\":{\"open_message_id\":\"om_1\",\"open_chat_id\":\"oc_1\"},"
                        + "\"action\":{\"value\":{"
                        + "\"action\":\"daily_report_feedback\","
                        + "\"feedback\":\"inaccurate\","
                        + "\"reportType\":\"competitor\","
                        + "\"reportDate\":\"2026-05-29\""
                        + "}}"
                        + "}"
                        + "}"));

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(String.valueOf(response.getBody())).contains("已收到反馈：不准");

        ArgumentCaptor<FeedbackCommand> captor = ArgumentCaptor.forClass(FeedbackCommand.class);
        verify(feedbackService).record(captor.capture());
        FeedbackCommand command = captor.getValue();
        assertThat(command.getEventId()).isEqualTo("evt-1");
        assertThat(command.getFeedback()).isEqualTo("inaccurate");
        assertThat(command.getReportDate()).isEqualTo("2026-05-29");
        assertThat(command.getOperatorOpenId()).isEqualTo("ou_1");
        assertThat(command.getOpenMessageId()).isEqualTo("om_1");
        verify(feedbackService).writeToBitableAsync(12L);
    }

    @Test
    void duplicate_event_does_not_start_second_async_write() throws Exception {
        DailyReportFeedbackEvent event = new DailyReportFeedbackEvent();
        event.setId(12L);
        event.setEventId("evt-1");
        when(feedbackService.record(any())).thenReturn(new RecordResult(event, false));

        ResponseEntity<Map<String, Object>> response = controller.callback(readTree(
                "{"
                        + "\"header\":{\"event_id\":\"evt-1\",\"token\":\"token-1\",\"event_type\":\"card.action.trigger\"},"
                        + "\"event\":{\"action\":{\"value\":{"
                        + "\"action\":\"daily_report_feedback\","
                        + "\"feedback\":\"useful\""
                        + "}}}"
                        + "}"));

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        verify(feedbackService).record(any());
        verify(feedbackService, never()).writeToBitableAsync(any());
    }

    @Test
    void invalid_token_is_rejected() throws Exception {
        ResponseEntity<Map<String, Object>> response = controller.callback(readTree(
                "{"
                        + "\"header\":{\"event_id\":\"evt-1\",\"token\":\"bad\",\"event_type\":\"card.action.trigger\"},"
                        + "\"event\":{\"action\":{\"value\":{"
                        + "\"action\":\"daily_report_feedback\","
                        + "\"feedback\":\"useful\""
                        + "}}}"
                        + "}"));

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
        verifyNoInteractions(feedbackService);
    }

    private JsonNode readTree(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
