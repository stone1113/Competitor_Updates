package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("llm_audit_log")
public class LlmAuditLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("agent_name")
    private String agentName;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_hash")
    private String promptHash;

    @TableField("input_tokens")
    private Integer inputTokens;

    @TableField("output_tokens")
    private Integer outputTokens;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;
}
