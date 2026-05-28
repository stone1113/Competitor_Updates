package com.nevinsight.model.entity.platform.content;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nevinsight.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xhs_note")
public class XhsNote extends BaseEntity {

    @TableField("brand_name")
    private String brandName;

    @TableField("note_id")
    private String noteId;

    @TableField("title")
    private String title;

    @TableField("`desc`")
    private String desc;

    @TableField("note_url")
    private String noteUrl;

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

    @TableField("`time`")
    private Long time;

    @TableField("type")
    private String type;

    @TableField("image_list")
    private String imageList;
}
