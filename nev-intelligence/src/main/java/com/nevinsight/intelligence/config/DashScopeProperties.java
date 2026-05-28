package com.nevinsight.intelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "nevinsight.dashscope")
public class DashScopeProperties {

    private String apiKey;
    private String reportModel = "qwen-max";
    private String screeningModel = "qwen-turbo";
    /** v8: 视觉模型用于官号海报 OCR + 政策提取 */
    private String visionModel = "qwen-vl-max-latest";
    private int timeoutSeconds = 120;
    private int maxTokens = 8192;
}
