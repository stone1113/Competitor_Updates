package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopNewsItemResponse {
    private String title;
    private String url;
    private String content;
    private String brandName;
    private String sourceTool;
    private String publishedDate;
    private Double relevanceScore;
    private String searchQuery;
}
