package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("data_source_config")
public class DataSourceConfig implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_name")
    private String sourceName;

    @TableField("brand_name")
    private String brandName;

    @TableField("keywords")
    private String keywords;

    @TableField("cron_expression")
    private String cronExpression;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("extra_config")
    private String extraConfig;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
