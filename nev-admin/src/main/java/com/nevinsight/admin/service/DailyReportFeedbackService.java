package com.nevinsight.admin.service;

import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import com.nevinsight.model.mapper.core.DailyReportFeedbackEventMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportFeedbackService {

    public static final String ACTION = "daily_report_feedback";
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_WRITTEN = "WRITTEN";
    public static final String STATUS_FAILED = "FAILED";

    private static final Map<String, String> FEEDBACK_LABELS = Map.of(
            "useful", "有用",
            "inaccurate", "不准",
            "too_long", "太长"
    );

    private final DailyReportFeedbackEventMapper mapper;
    private final FeishuBitableCliService bitableCliService;

    @Transactional
    public RecordResult record(FeedbackCommand command) {
        DailyReportFeedbackEvent existing = mapper.findByEventId(command.getEventId());
        if (existing != null) {
            return new RecordResult(existing, false);
        }

        DailyReportFeedbackEvent event = new DailyReportFeedbackEvent();
        event.setEventId(command.getEventId());
        event.setReportType(defaultBlank(command.getReportType(), "competitor"));
        event.setReportDate(parseDate(command.getReportDate()));
        event.setFeedbackType(command.getFeedback());
        event.setFeedbackLabel(labelOf(command.getFeedback()));
        event.setOperatorOpenId(command.getOperatorOpenId());
        event.setOperatorUnionId(command.getOperatorUnionId());
        event.setOpenMessageId(command.getOpenMessageId());
        event.setOpenChatId(command.getOpenChatId());
        event.setBitableStatus(STATUS_RECEIVED);
        try {
            mapper.insert(event);
            return new RecordResult(event, true);
        } catch (DuplicateKeyException e) {
            DailyReportFeedbackEvent duplicate = mapper.findByEventId(command.getEventId());
            return new RecordResult(duplicate, false);
        }
    }

    @Async
    public void writeToBitableAsync(Long id) {
        if (id == null) return;
        DailyReportFeedbackEvent event = mapper.selectById(id);
        if (event == null) return;
        try {
            FeishuBitableCliService.WriteResult result = bitableCliService.append(event);
            event.setBitableStatus(result.isSuccess() ? STATUS_WRITTEN : STATUS_FAILED);
            event.setBitableRecordId(result.getRecordId());
            event.setLastError(result.isSuccess() ? null : trimError(result.getMessage()));
        } catch (Exception e) {
            log.warn("[DailyReportFeedback] write bitable failed eventId={}: {}", event.getEventId(), e.getMessage());
            event.setBitableStatus(STATUS_FAILED);
            event.setLastError(trimError(e.getMessage()));
        }
        mapper.updateById(event);
    }

    public static String labelOf(String feedback) {
        return FEEDBACK_LABELS.getOrDefault(feedback, "未知");
    }

    public static boolean isSupportedFeedback(String feedback) {
        return FEEDBACK_LABELS.containsKey(feedback);
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String defaultBlank(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String trimError(String message) {
        if (message == null) return null;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    @Data
    @Builder
    public static class FeedbackCommand {
        private String eventId;
        private String reportType;
        private String reportDate;
        private String feedback;
        private String operatorOpenId;
        private String operatorUnionId;
        private String openMessageId;
        private String openChatId;
    }

    @Data
    @RequiredArgsConstructor
    public static class RecordResult {
        private final DailyReportFeedbackEvent event;
        private final boolean created;
    }
}
