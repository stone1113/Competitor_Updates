package com.nevinsight.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuBitableCliServiceTest {

    @Test
    void buildFields_maps_feedback_event_to_bitable_columns() {
        FeishuBitableCliService service = new FeishuBitableCliService(new ObjectMapper());
        DailyReportFeedbackEvent event = new DailyReportFeedbackEvent();
        event.setEventId("evt-1");
        event.setReportType("competitor");
        event.setReportDate(LocalDate.of(2026, 5, 29));
        event.setFeedbackLabel("太长");
        event.setOperatorOpenId("ou_1");
        event.setOperatorUnionId("on_1");
        event.setOpenMessageId("om_1");
        event.setOpenChatId("oc_1");

        Map<String, Object> fields = service.buildFields(event);

        assertThat(fields).containsEntry("反馈类型", "太长");
        assertThat(fields).containsEntry("日报类型", "competitor");
        assertThat(fields).doesNotContainKey("品牌");
        assertThat(fields).containsEntry("日报日期", "2026-05-29");
        assertThat(fields).containsEntry("飞书用户open_id", "ou_1");
        assertThat(fields).containsEntry("飞书用户union_id", "on_1");
        assertThat(fields).containsEntry("飞书消息open_message_id", "om_1");
        assertThat(fields).containsEntry("飞书群open_chat_id", "oc_1");
        assertThat(fields).containsEntry("回调event_id", "evt-1");
        assertThat(fields.get("反馈时间")).isInstanceOf(String.class);
    }

    @Test
    void buildCommand_wraps_windows_cmd_cli() {
        FeishuBitableCliService service = new FeishuBitableCliService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "cliPath", "lark-cli.cmd");
        ReflectionTestUtils.setField(service, "cliAs", "user");
        ReflectionTestUtils.setField(service, "bitableAppToken", "base-token");
        ReflectionTestUtils.setField(service, "bitableTableId", "table-id");

        List<String> command = service.buildCommand("{\"反馈类型\":\"有用\"}");

        assertThat(command).startsWith("cmd.exe", "/c", "lark-cli.cmd", "base", "+record-upsert");
        assertThat(command).contains("--base-token", "base-token", "--table-id", "table-id");
        assertThat(command).contains("--json", "{\"反馈类型\":\"有用\"}", "--as", "user");
    }

    @Test
    void extractRecordId_reads_lark_cli_record_id_list() throws Exception {
        FeishuBitableCliService service = new FeishuBitableCliService(new ObjectMapper());
        Method method = FeishuBitableCliService.class.getDeclaredMethod("extractRecordId", String.class);
        method.setAccessible(true);

        String recordId = (String) method.invoke(service,
                "{\"data\":{\"record\":{\"record_id_list\":[\"rec123\"]}}}");

        assertThat(recordId).isEqualTo("rec123");
    }
}
