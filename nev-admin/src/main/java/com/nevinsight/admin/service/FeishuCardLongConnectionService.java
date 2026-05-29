package com.nevinsight.admin.service;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.*;
import com.lark.oapi.ws.Client;
import com.nevinsight.admin.service.DailyReportFeedbackService.FeedbackCommand;
import com.nevinsight.admin.service.DailyReportFeedbackService.RecordResult;
import com.nevinsight.model.entity.core.DailyReportFeedbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuCardLongConnectionService implements SmartLifecycle {

    private final DailyReportFeedbackService feedbackService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${nevinsight.feishu.app-id:}")
    private String appId;

    @Value("${nevinsight.feishu.app-secret:}")
    private String appSecret;

    @Value("${nevinsight.feishu.card-long-connection-enabled:true}")
    private boolean enabled;

    private Thread worker;

    @Override
    public void start() {
        if (!enabled) {
            log.info("[FeishuCardLongConnection] disabled");
            return;
        }
        if (isBlank(appId) || isBlank(appSecret)) {
            log.info("[FeishuCardLongConnection] app-id/app-secret not configured; skip start");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        worker = new Thread(this::runClient, "feishu-card-long-connection");
        worker.setDaemon(true);
        worker.start();
    }

    private void runClient() {
        try {
            EventDispatcher dispatcher = EventDispatcher.newBuilder("", "")
                    .onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                        @Override
                        public P2CardActionTriggerResponse handle(P2CardActionTrigger event) {
                            return handleCardAction(event);
                        }
                    })
                    .build();

            Client client = new Client.Builder(appId.trim(), appSecret.trim())
                    .eventHandler(dispatcher)
                    .autoReconnect(true)
                    .build();
            log.info("[FeishuCardLongConnection] starting websocket client");
            client.start();
        } catch (Exception e) {
            running.set(false);
            log.error("[FeishuCardLongConnection] websocket client stopped: {}", e.getMessage(), e);
        }
    }

    P2CardActionTriggerResponse handleCardAction(P2CardActionTrigger event) {
        try {
            P2CardActionTriggerData data = event == null ? null : event.getEvent();
            Map<String, Object> value = data == null || data.getAction() == null
                    ? Map.of()
                    : data.getAction().getValue();
            String action = text(value, "action");
            if (!DailyReportFeedbackService.ACTION.equals(action)) {
                return response("info", "已忽略非日报反馈操作");
            }

            String feedback = text(value, "feedback");
            if (!DailyReportFeedbackService.isSupportedFeedback(feedback)) {
                return response("error", "未知反馈类型");
            }

            FeedbackCommand command = FeedbackCommand.builder()
                    .eventId(headerValue(event, "eventId"))
                    .reportType(text(value, "reportType"))
                    .reportDate(text(value, "reportDate"))
                    .feedback(feedback)
                    .operatorOpenId(operatorValue(data, "openId"))
                    .operatorUnionId(operatorValue(data, "unionId"))
                    .openMessageId(contextValue(data, "openMessageId"))
                    .openChatId(contextValue(data, "openChatId"))
                    .build();

            RecordResult result = feedbackService.record(command);
            DailyReportFeedbackEvent saved = result.getEvent();
            if (result.isCreated() && saved != null) {
                feedbackService.writeToBitableAsync(saved.getId());
            }
            log.info("[FeishuCardLongConnection] feedback received eventId={} feedback={} created={}",
                    command.getEventId(), feedback, result.isCreated());
            return response("info", "已收到反馈：" + DailyReportFeedbackService.labelOf(feedback));
        } catch (Exception e) {
            log.warn("[FeishuCardLongConnection] handle card action failed: {}", e.getMessage(), e);
            return response("error", "反馈处理失败，请稍后重试");
        }
    }

    private static P2CardActionTriggerResponse response(String type, String content) {
        CallBackToast toast = new CallBackToast();
        toast.setType(type);
        toast.setContent(content);
        toast.setI18n(Map.of("zh_cn", content, "en_us", content));

        P2CardActionTriggerResponse response = new P2CardActionTriggerResponse();
        response.setToast(toast);
        return response;
    }

    private static String headerValue(P2CardActionTrigger event, String field) {
        if (event == null || event.getHeader() == null) return "";
        if ("eventId".equals(field)) return defaultBlank(event.getHeader().getEventId());
        return "";
    }

    private static String operatorValue(P2CardActionTriggerData data, String field) {
        CallBackOperator operator = data == null ? null : data.getOperator();
        if (operator == null) return "";
        if ("openId".equals(field)) return defaultBlank(operator.getOpenId());
        if ("unionId".equals(field)) return defaultBlank(operator.getUnionId());
        return "";
    }

    private static String contextValue(P2CardActionTriggerData data, String field) {
        CallBackContext context = data == null ? null : data.getContext();
        if (context == null) return "";
        if ("openMessageId".equals(field)) return defaultBlank(context.getOpenMessageId());
        if ("openChatId".equals(field)) return defaultBlank(context.getOpenChatId());
        return "";
    }

    private static String text(Map<String, Object> value, String key) {
        if (value == null || key == null) return "";
        Object object = value.get(key);
        return object == null ? "" : String.valueOf(object);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultBlank(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
