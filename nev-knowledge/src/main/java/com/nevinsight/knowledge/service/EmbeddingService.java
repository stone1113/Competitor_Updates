package com.nevinsight.knowledge.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmbeddingService {

    @Value("${nevinsight.dashscope.api-key:}")
    private String apiKey;

    @Value("${nevinsight.dashscope.embedding-model:text-embedding-v3}")
    private String embeddingModel;

    /**
     * 单条文本向量化
     */
    public List<Float> embed(String text) {
        List<List<Float>> result = embedBatch(Collections.singletonList(text));
        return result.isEmpty() ? Collections.emptyList() : result.get(0);
    }

    /**
     * 批量文本向量化 (每批最多32条)
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        List<List<Float>> allEmbeddings = new ArrayList<>();
        int batchSize = 32;

        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            try {
                TextEmbedding te = new TextEmbedding();
                TextEmbeddingParam param = TextEmbeddingParam.builder()
                        .model(embeddingModel)
                        .texts(batch)
                        .apiKey(apiKey)
                        .build();
                TextEmbeddingResult result = te.call(param);
                result.getOutput().getEmbeddings().forEach(emb -> {
                    List<Float> vec = emb.getEmbedding().stream()
                            .map(Double::floatValue)
                            .collect(Collectors.toList());
                    allEmbeddings.add(vec);
                });
            } catch (Exception e) {
                log.error("[Embedding] 批次 {}-{} 向量化失败: {}", i, i + batch.size(), e.getMessage());
                for (int j = 0; j < batch.size(); j++) {
                    allEmbeddings.add(Collections.emptyList());
                }
            }
        }
        return allEmbeddings;
    }
}
