package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("talking_point_version")
public class TalkingPointVersion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("talking_point_id")
    private Long talkingPointId;

    @TableField("version")
    private Integer version;

    @TableField("content")
    private String content;

    @TableField("change_reason")
    private String changeReason;

    @TableField("author")
    private String author;

    @TableField("is_current")
    private Boolean isCurrent;

    @TableField("create_time")
    private LocalDateTime createTime;
}
