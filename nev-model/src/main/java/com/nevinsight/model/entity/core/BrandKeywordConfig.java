package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("brand_keyword_config")
public class BrandKeywordConfig extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("keyword")
    private String keyword;

    /** brand | industry */
    @TableField("category")
    private String category;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("remark")
    private String remark;
}
