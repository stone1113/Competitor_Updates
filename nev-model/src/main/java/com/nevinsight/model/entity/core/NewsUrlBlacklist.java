package com.nevinsight.model.entity.core;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新闻 URL 黑名单。Bocha 采集时按 url.contains(pattern) 直接过滤。
 *
 * 典型 pattern：autohome.com.cn/dealer/、/koubei/、/photolist/ 等
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("news_url_blacklist")
public class NewsUrlBlacklist extends BaseEntity {

    @TableField("pattern")
    private String pattern;

    @TableField("reason")
    private String reason;

    @TableField("is_enabled")
    private Boolean isEnabled;
}
