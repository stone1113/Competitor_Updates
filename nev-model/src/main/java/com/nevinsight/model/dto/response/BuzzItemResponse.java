package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuzzItemResponse {
    private String platform;
    private String platformName;
    private String contentId;
    private String title;
    private String content;
    private String url;
    private String nickname;
    private String likedCount;
    private String commentCount;
    private String shareCount;
    private String viewCount;
    private String collectedCount;
    private Long time;
    private long engagementScore;
}
