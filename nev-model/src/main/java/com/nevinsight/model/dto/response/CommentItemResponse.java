package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentItemResponse {
    private String commentId;
    private String content;
    private String nickname;
    private String likeCount;
    private String subCommentCount;
    /** create_time in original unit (xhs/ks: ms; dy/bili/wb: seconds) — frontend normalizes */
    private Long time;
}
