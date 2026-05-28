package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("competitor_dimension_score")
public class CompetitorDimensionScore implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("brand_name")
    private String brandName;

    @TableField("report_date")
    private LocalDate reportDate;

    @TableField("dimension")
    private String dimension;

    @TableField("score")
    private BigDecimal score;

    @TableField("evidence")
    private String evidence;

    @TableField("source")
    private String source;

    @TableField("create_time")
    private LocalDateTime createTime;
}
