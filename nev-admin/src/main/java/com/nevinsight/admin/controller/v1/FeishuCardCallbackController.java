package com.nevinsight.admin.controller.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.nevinsight.admin.service.DailyReportFeedbackService;
import com.nevinsight.admin.service.DailyReportFeedbackService.FeedbackCommand;
import com.nevinsight.admin.service.DailyReportFeedbackService.RecordResult;
import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/feishu-card")
@RequiredArgsConstructor
public class FeishuCardCallbackController {

    private final DailyReportFeedbackService feedbackService;

    @Value("${nevinsight.feishu.card-verification-token:}")
    private String verificationToken;

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody JsonNode body) {
        String challenge = text(body, "/challenge");
        if (!challenge.isEmpty()) {
            return ResponseEntity.ok(Map.of("challenge", challenge));
        }

        if (!isTokenValid(body)) {
            log.warn("[FeishuCardCallback] invalid token eventId={}", firstNonBlank(
                    text(body, "/header/event_id"), text(body, "/event_id")));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(toast("error", "反馈校验失败"));
        }

        String eventType = firstNonBlank(text(body, "/header/event_type"), text(body, "/event_type"));
        if (!eventType.isEmpty() && !"card.action.trigger".equals(eventType)) {
            return ResponseEntity.ok(toast("info", "已忽略非日报反馈操作"));
        }

        JsonNode value = body.at("/event/action/value");
        if (value.isMissingNode() || value.isNull()) value = body.at("/action/value");
        String action = text(value, "action");
        if (!DailyReportFeedbackService.ACTION.equals(action)) {
            return ResponseEntity.ok(toast("info", "已忽略非日报反馈操作"));
        }

        String feedback = text(value, "feedback");
        if (!DailyReportFeedbackService.isSupportedFeedback(feedback)) {
            return ResponseEntity.ok(toast("error", "未知反馈类型"));
        }

        FeedbackCommand command = FeedbackCommand.builder()
                .eventId(firstNonBlank(text(body, "/header/event_id"), text(body, "/event_id")))
                .reportType(text(value, "reportType"))
                .reportDate(text(value, "reportDate"))
                .feedback(feedback)
                .operatorOpenId(firstNonBlank(text(body, "/event/operator/open_id"),
                        text(body, "/operator/open_id")))
                .operatorUnionId(firstNonBlank(text(body, "/event/operator/union_id"),
                        text(body, "/operator/union_id")))
                .openMessageId(firstNonBlank(text(body, "/event/context/open_message_id"),
                        text(body, "/context/open_message_id")))
                .openChatId(firstNonBlank(text(body, "/event/context/open_chat_id"),
                        text(body, "/context/open_chat_id")))
                .build();

        RecordResult result = feedbackService.record(command);
        DailyReportFeedbackEvent event = result.getEvent();
        if (result.isCreated() && event != null) {
            feedbackService.writeToBitableAsync(event.getId());
        }
        return ResponseEntity.ok(toast("info", "已收到反馈：" + DailyReportFeedbackService.labelOf(feedback)));
    }

    private boolean isTokenValid(JsonNode body) {
        if (verificationToken == null || verificationToken.isBlank()) {
            return true;
        }
        String token = firstNonBlank(text(body, "/header/token"), text(body, "/token"));
        return verificationToken.equals(token);
    }

    private static Map<String, Object> toast(String type, String content) {
        Map<String, Object> toast = new LinkedHashMap<>();
        toast.put("type", type);
        toast.put("content", content);
        toast.put("i18n", Map.of("zh_cn", content, "en_us", content));
        return Map.of("toast", toast);
    }

    private static String text(JsonNode node, String pointerOrField) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode value = pointerOrField.startsWith("/")
                ? node.at(pointerOrField)
                : node.path(pointerOrField);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
