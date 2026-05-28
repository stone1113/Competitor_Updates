package com.nevinsight.model.entity.platform.content;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bilibili_video")
public class BilibiliVideo extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("video_id")
    private Long videoId;

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
    private Integer likedCount;

    @TableField("video_comment")
    private String videoComment;

    @TableField("video_share_count")
    private String videoShareCount;

    @TableField("video_play_count")
    private String videoPlayCount;

    @TableField("video_favorite_count")
    private String videoFavoriteCount;

    @TableField("video_danmaku")
    private String videoDanmaku;

    @TableField("create_time")
    private Long createTime;
}
