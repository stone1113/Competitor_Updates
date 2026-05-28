package com.nevinsight.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "nevinsight.milvus")
public class MilvusProperties {

    private String host = "localhost";
    private int port = 19530;
    private String collectionName = "knowledge_entry";
    private int embeddingDimension = 1024;
}
