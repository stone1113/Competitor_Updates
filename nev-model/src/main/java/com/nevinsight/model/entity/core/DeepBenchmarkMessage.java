package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * v9.5 对话式深度对标 — 单条消息（user / assistant / system）。
 * metadata 用 JSON 字符串存（mapper 序列化），含 analyst_results / chart_data 等。
 */
@Data
@TableName("deep_benchmark_message")
public class DeepBenchmarkMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    /** user | assistant | system */
    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    /** JSON 字符串：{analystResults, chartData, agentType, elapsedMs, ...} */
    @TableField("metadata")
    private String metadata;

    @TableField("add_ts")
    private Long addTs;
}
