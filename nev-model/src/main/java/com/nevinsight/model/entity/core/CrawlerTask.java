package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crawler_task")
public class CrawlerTask extends BaseEntity {

    @TableField("remote_task_id")
    private String remoteTaskId;

    @TableField("brand_name")
    private String brandName;

    @TableField("platform")
    private String platform;

    /** JSON array string, e.g. ["猛士917","猛士M9"] */
    @TableField("keywords")
    private String keywords;

    @TableField("max_notes")
    private Integer maxNotes;

    @TableField("enable_comments")
    private Boolean enableComments;

    @TableField("login_type")
    private String loginType;

    /** PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT */
    @TableField("status")
    private String status;

    @TableField("started_at")
    private Long startedAt;

    @TableField("finished_at")
    private Long finishedAt;

    @TableField("duration_sec")
    private Integer durationSec;

    @TableField("error_message")
    private String errorMessage;
}
