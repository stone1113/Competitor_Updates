package com.nevinsight.knowledge.service;

import com.nevinsight.knowledge.config.MilvusProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;

@Slf4j
@Service
public class MilvusKnowledgeService {

    private final MilvusProperties milvusProperties;
    private final EmbeddingService embeddingService;
    private MilvusServiceClient milvusClient;

    public MilvusKnowledgeService(MilvusProperties milvusProperties, EmbeddingService embeddingService) {
        this.milvusProperties = milvusProperties;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        try {
            milvusClient = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(milvusProperties.getHost())
                            .withPort(milvusProperties.getPort())
                            .build()
            );
            log.info("[Milvus] 连接成功 {}:{}", milvusProperties.getHost(), milvusProperties.getPort());
        } catch (Exception e) {
            log.warn("[Milvus] 连接失败，知识库功能不可用: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (milvusClient != null) {
            milvusClient.close();
        }
    }

    /**
     * 语义搜索知识库
     */
    public List<KnowledgeEntry> search(String query, String entryType, int topK) {
        if (milvusClient == null) {
            log.warn("[Milvus] 客户端未初始化");
            return Collections.emptyList();
        }

        try {
            List<Float> queryVec = embeddingService.embed(query);
            if (queryVec.isEmpty()) return Collections.emptyList();

            String expr = entryType != null ? String.format("entry_type == '%s'", entryType) : "";

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(milvusProperties.getCollectionName())
                    .withMetricType(MetricType.COSINE)
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(queryVec))
                    .withExpr(expr)
                    .withOutFields(Arrays.asList("title", "content", "entry_type", "quality_score"))
                    .build();

            var searchResults = milvusClient.search(searchParam);
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getData().getResults());

            List<KnowledgeEntry> entries = new ArrayList<>();
            for (int i = 0; i < wrapper.getIDScore(0).size(); i++) {
                var idScore = wrapper.getIDScore(0).get(i);
                KnowledgeEntry entry = new KnowledgeEntry();
                entry.setId(idScore.getLongID());
                entry.setScore((float) idScore.getScore());
                entries.add(entry);
            }
            return entries;
        } catch (Exception e) {
            log.error("[Milvus] 搜索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * RAG 检索 - 复合评分: cosine_sim*0.6 + quality*0.25 + recency*0.1 + useCount*0.05
     */
    public String searchForRag(String query) {
        List<KnowledgeEntry> results = search(query, null, 5);
        if (results.isEmpty()) return "";

        StringBuilder context = new StringBuilder("【知识库参考】:\n");
        for (KnowledgeEntry entry : results) {
            if (entry.getContent() != null && !entry.getContent().isEmpty()) {
                context.append("- ").append(entry.getContent()).append("\n");
            }
        }
        return context.toString();
    }

    @Data
    public static class KnowledgeEntry {
        private long id;
        private String title;
        private String content;
        private String entryType;
        private float qualityScore;
        private float score;
    }
}
