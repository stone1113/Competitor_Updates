package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("cleaning_rule")
public class CleaningRule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rule_type")
    private String ruleType;

    @TableField("rule_name")
    private String ruleName;

    @TableField("rule_config")
    private String ruleConfig;

    @TableField("priority")
    private Integer priority;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
