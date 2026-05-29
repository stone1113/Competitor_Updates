package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_report_feedback_event")
public class DailyReportFeedbackEvent extends BaseEntity {

    @TableField("event_id")
    private String eventId;

    @TableField("report_type")
    private String reportType;

    @TableField("report_date")
    private LocalDate reportDate;

    @TableField("feedback_type")
    private String feedbackType;

    @TableField("feedback_label")
    private String feedbackLabel;

    @TableField("operator_open_id")
    private String operatorOpenId;

    @TableField("operator_union_id")
    private String operatorUnionId;

    @TableField("open_message_id")
    private String openMessageId;

    @TableField("open_chat_id")
    private String openChatId;

    @TableField("bitable_status")
    private String bitableStatus;

    @TableField("bitable_record_id")
    private String bitableRecordId;

    @TableField("last_error")
    private String lastError;
}
