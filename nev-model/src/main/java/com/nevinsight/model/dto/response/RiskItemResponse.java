package com.nevinsight.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskItemResponse {
    private String platform;
    private String platformName;
    private String contentId;
    private String title;
    private String url;
    private String nickname;
    private Long time;
    private String sentimentLabel;
}
