package com.nevinsight.model.entity.platform.content;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("douyin_aweme")
public class DouyinAweme extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("aweme_id")
    private Long awemeId;

    @TableField("title")
    private String title;

    @TableField("`desc`")
    private String desc;

    @TableField("aweme_url")
    private String awemeUrl;

    @TableField("nickname")
    private String nickname;

    @TableField("user_id")
    private String userId;

    @TableField("liked_count")
    private String likedCount;

    @TableField("comment_count")
    private String commentCount;

    @TableField("share_count")
    private String shareCount;

    @TableField("collected_count")
    private String collectedCount;

    @TableField("create_time")
    private Long createTime;
}
