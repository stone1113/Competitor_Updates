package com.nevinsight.model.entity.platform.content;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kuaishou_video")
public class KuaishouVideo extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("video_id")
    private String videoId;

    @TableField("title")
    private String title;

    @TableField("`desc`")
    private String desc;

    @TableField("video_url")
    private String videoUrl;

    @TableField("nickname")
    private String nickname;

    @TableField("user_id")
    private String userId;

    @TableField("liked_count")
    private String likedCount;

    @TableField("viewd_count")
    private String viewdCount;

    @TableField("create_time")
    private Long createTime;
}
