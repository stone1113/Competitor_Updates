package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 汽车之家车系抓取配置表。前端可维护，每周由 AutohomeSyncService 读取触发抓取。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("autohome_series_config")
public class AutohomeSeriesConfig extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("model_name")
    private String modelName;

    /** 汽车之家 seriesid，如 "7172" */
    @TableField("series_id")
    private String seriesId;

    /** 盖世汽车 series_id（URL /qcxl/cxxl/Y/M/{id} 末尾段）；NULL=不抓盖世销量 */
    @TableField("gasgoo_series_id")
    private String gasgooSeriesId;

    /** self | competitor */
    @TableField("role")
    private String role;

    /** 主对标关系：填本品车型名（如「猛士M817」「猛士917」）；NULL 表示仅追踪不入对标矩阵 */
    @TableField("vs_self_model")
    private String vsSelfModel;

    @TableField("is_enabled")
    private Boolean isEnabled;

    @TableField("remark")
    private String remark;
}
