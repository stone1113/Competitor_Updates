package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/** 单例配置：crawler_schedule_config 表只允许一行（id=1） */
@Data
@TableName("crawler_schedule_config")
public class CrawlerScheduleConfig implements Serializable {

    @TableId(type = IdType.INPUT)
    private Integer id;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("cron")
    private String cron;

    @TableField("last_modify_ts")
    private Long lastModifyTs;
}
