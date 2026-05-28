package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuzzCommentResponse {
    private String commentId;
    private String content;
    private String nickname;
    private String likeCount;
    private String subCommentCount;
    private Long time;
}
