package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * v9.5 对话式深度对标 — 会话实体。
 * 不继承 BaseEntity（add_ts 在 service 显式设置，避免 MetaHandler 干扰 last_active_ts）。
 */
@Data
@TableName("deep_benchmark_session")
public class DeepBenchmarkSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户隔离 token（v9.5 用浏览器 localStorage UUID） */
    @TableField("user_token")
    private String userToken;

    /** v9.6 飞书登录后填充 */
    @TableField("feishu_user_id")
    private String feishuUserId;

    @TableField("title")
    private String title;

    @TableField("self_model")
    private String selfModel;

    /** 逗号分隔 */
    @TableField("competitor_models")
    private String competitorModels;

    @TableField("add_ts")
    private Long addTs;

    @TableField("last_active_ts")
    private Long lastActiveTs;
}
