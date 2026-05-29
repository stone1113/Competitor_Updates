package com.nevinsight.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuBitableCliService {

    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FEEDBACK_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(CN_ZONE);

    private final ObjectMapper objectMapper;

    @Value("${nevinsight.feishu.cli-path:lark-cli.cmd}")
    private String cliPath;

    @Value("${nevinsight.feishu.cli-as:user}")
    private String cliAs;

    @Value("${nevinsight.feishu.bitable-app-token:}")
    private String bitableAppToken;

    @Value("${nevinsight.feishu.bitable-table-id:}")
    private String bitableTableId;

    @Value("${nevinsight.feishu.cli-timeout-ms:10000}")
    private long cliTimeoutMs;

    public WriteResult append(DailyReportFeedbackEvent event) throws Exception {
        if (isBlank(cliPath) || isBlank(bitableAppToken) || isBlank(bitableTableId)) {
            return new WriteResult(false, null, "FEISHU_CLI_PATH/FEISHU_BITABLE_APP_TOKEN/FEISHU_BITABLE_TABLE_ID 未配置");
        }

        List<String> command = buildCommand(objectMapper.writeValueAsString(buildFields(event)));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(cliTimeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new WriteResult(false, null, "飞书 CLI 写表超时");
        }
        String output = readOutput(process);
        if (process.exitValue() != 0) {
            return new WriteResult(false, null, output);
        }
        return new WriteResult(true, extractRecordId(output), output);
    }

    Map<String, Object> buildFields(DailyReportFeedbackEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("反馈时间", FEEDBACK_TIME_FMT.format(Instant.now()));
        fields.put("反馈类型", nullToBlank(event.getFeedbackLabel()));
        fields.put("日报类型", nullToBlank(event.getReportType()));
        fields.put("日报日期", event.getReportDate() == null ? "" : event.getReportDate().toString());
        fields.put("飞书用户open_id", nullToBlank(event.getOperatorOpenId()));
        fields.put("飞书用户union_id", nullToBlank(event.getOperatorUnionId()));
        fields.put("飞书消息open_message_id", nullToBlank(event.getOpenMessageId()));
        fields.put("飞书群open_chat_id", nullToBlank(event.getOpenChatId()));
        fields.put("回调event_id", nullToBlank(event.getEventId()));
        return fields;
    }

    List<String> buildCommand(String fieldsJson) {
        List<String> command = new ArrayList<>();
        String normalized = cliPath == null ? "" : cliPath.trim().toLowerCase();
        if (normalized.endsWith(".cmd") || normalized.endsWith(".bat")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(cliPath);
        command.add("base");
        command.add("+record-upsert");
        command.add("--base-token");
        command.add(bitableAppToken);
        command.add("--table-id");
        command.add(bitableTableId);
        command.add("--json");
        command.add(fieldsJson);
        command.add("--as");
        command.add(cliAs);
        return command;
    }

    private String extractRecordId(String output) {
        if (isBlank(output)) return null;
        try {
            JsonNode root = objectMapper.readTree(output);
            JsonNode recordId = root.findValue("record_id");
            if (recordId == null) recordId = root.findValue("recordId");
            if (recordId == null) {
                JsonNode recordIdList = root.findValue("record_id_list");
                if (recordIdList != null && recordIdList.isArray() && recordIdList.size() > 0) {
                    recordId = recordIdList.get(0);
                }
            }
            return recordId == null ? null : recordId.asText(null);
        } catch (Exception e) {
            log.debug("[FeishuBitableCli] output is not json: {}", output);
            return null;
        }
    }

    private static String readOutput(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String nullToBlank(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    @Data
    @AllArgsConstructor
    public static class WriteResult {
        private boolean success;
        private String recordId;
        private String message;
    }
}
