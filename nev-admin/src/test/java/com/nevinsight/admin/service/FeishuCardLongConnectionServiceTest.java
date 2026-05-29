package com.nevinsight.admin.service;

import com.lark.oapi.event.cardcallback.model.*;
import com.lark.oapi.event.model.Header;
import com.nevinsight.admin.service.DailyReportFeedbackService.FeedbackCommand;
import com.nevinsight.admin.service.DailyReportFeedbackService.RecordResult;
import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FeishuCardLongConnectionServiceTest {

    @Test
    void card_action_records_feedback_and_returns_toast() {
        DailyReportFeedbackService feedbackService = mock(DailyReportFeedbackService.class);
        DailyReportFeedbackEvent saved = new DailyReportFeedbackEvent();
        saved.setId(23L);
        saved.setEventId("evt-1");
        when(feedbackService.record(any())).thenReturn(new RecordResult(saved, true));

        FeishuCardLongConnectionService service = new FeishuCardLongConnectionService(feedbackService);
        P2CardActionTriggerResponse response = service.handleCardAction(event());

        assertThat(response.getToast().getType()).isEqualTo("info");
        assertThat(response.getToast().getContent()).contains("已收到反馈");

        ArgumentCaptor<FeedbackCommand> captor = ArgumentCaptor.forClass(FeedbackCommand.class);
        verify(feedbackService).record(captor.capture());
        FeedbackCommand command = captor.getValue();
        assertThat(command.getEventId()).isEqualTo("evt-1");
        assertThat(command.getFeedback()).isEqualTo("useful");
        assertThat(command.getReportType()).isEqualTo("competitor");
        assertThat(command.getReportDate()).isEqualTo("2026-05-29");
        assertThat(command.getOperatorOpenId()).isEqualTo("ou_1");
        assertThat(command.getOperatorUnionId()).isEqualTo("on_1");
        assertThat(command.getOpenMessageId()).isEqualTo("om_1");
        assertThat(command.getOpenChatId()).isEqualTo("oc_1");
        verify(feedbackService).writeToBitableAsync(23L);
    }

    @Test
    void duplicate_feedback_does_not_write_bitable_again() {
        DailyReportFeedbackService feedbackService = mock(DailyReportFeedbackService.class);
        when(feedbackService.record(any())).thenReturn(new RecordResult(new DailyReportFeedbackEvent(), false));

        FeishuCardLongConnectionService service = new FeishuCardLongConnectionService(feedbackService);
        P2CardActionTriggerResponse response = service.handleCardAction(event());

        assertThat(response.getToast().getType()).isEqualTo("info");
        verify(feedbackService).record(any());
        verify(feedbackService, never()).writeToBitableAsync(any());
    }

    private static P2CardActionTrigger event() {
        Header header = new Header();
        header.setEventId("evt-1");
        header.setEventType("card.action.trigger");

        CallBackAction action = new CallBackAction();
        action.setValue(Map.of(
                "action", "daily_report_feedback",
                "feedback", "useful",
                "reportType", "competitor",
                "reportDate", "2026-05-29"));

        CallBackOperator operator = new CallBackOperator();
        operator.setOpenId("ou_1");
        operator.setUnionId("on_1");

        CallBackContext context = new CallBackContext();
        context.setOpenMessageId("om_1");
        context.setOpenChatId("oc_1");

        P2CardActionTriggerData data = new P2CardActionTriggerData();
        data.setAction(action);
        data.setOperator(operator);
        data.setContext(context);

        P2CardActionTrigger event = new P2CardActionTrigger();
        event.setHeader(header);
        event.setEvent(data);
        return event;
    }
}
