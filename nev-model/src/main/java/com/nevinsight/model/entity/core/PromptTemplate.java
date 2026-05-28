package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("prompt_template")
public class PromptTemplate implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("agent_name")
    private String agentName;

    @TableField("template_name")
    private String templateName;

    @TableField("template_content")
    private String templateContent;

    @TableField("variables")
    private String variables;

    @TableField("version")
    private Integer version;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
