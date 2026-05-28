package com.nevinsight.model.entity.platform.content;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("weibo_note")
public class WeiboNote extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("note_id")
    private Long noteId;

    @TableField("content")
    private String content;

    @TableField("note_url")
    private String noteUrl;

    @TableField("nickname")
    private String nickname;

    @TableField("user_id")
    private String userId;

    @TableField("liked_count")
    private String likedCount;

    @TableField("comments_count")
    private String commentsCount;

    @TableField("shared_count")
    private String sharedCount;

    @TableField("create_time")
    private Long createTime;
}
