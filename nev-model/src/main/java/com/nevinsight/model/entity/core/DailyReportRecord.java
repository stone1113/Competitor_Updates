package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("daily_report_record")
public class DailyReportRecord extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("report_date")
    private LocalDate reportDate;

    @TableField("card_json")
    private String cardJson;

    @TableField("sections_json")
    private String sectionsJson;

    @TableField("kpi_json")
    private String kpiJson;

    @TableField("build_context_json")
    private String buildContextJson;

    @TableField("status")
    private String status;

    @TableField("pushed_test_ts")
    private Long pushedTestTs;

    @TableField("pushed_prod_ts")
    private Long pushedProdTs;

    @TableField("test_push_count")
    private Integer testPushCount;

    @TableField("prod_push_count")
    private Integer prodPushCount;

    @TableField("last_error")
    private String lastError;
}
