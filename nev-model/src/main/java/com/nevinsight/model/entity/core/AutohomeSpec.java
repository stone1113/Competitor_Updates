package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 汽车之家车型参数明细。一行 = 一个车款的一个参数项在某天的值。
 * 不继承 BaseEntity（没有 last_modify_ts；新增即不变）。
 */
@Data
@TableName("autohome_spec")
public class AutohomeSpec implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("series_id")
    private String seriesId;

    @TableField("spec_id")
    private String specId;

    @TableField("spec_name")
    private String specName;

    @TableField("param_category")
    private String paramCategory;

    @TableField("param_name")
    private String paramName;

    @TableField("param_value")
    private String paramValue;

    @TableField("crawl_date")
    private LocalDate crawlDate;

    @TableField(value = "add_ts", fill = FieldFill.INSERT)
    private Long addTs;
}
