package com.nevinsight.intelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAGFlow 配置。RAGFlow 由用户独立部署（参考 /Users/anliwu/claude-pro/ragflow），
 * Java 仅通过 HTTP 调用其 retrieve / upload / delete / list 接口。
 */
@Data
@Component
@ConfigurationProperties(prefix = "nevinsight.ragflow")
public class RagflowProperties {

    /** RAGFlow HTTP 服务地址，Mac 上默认 http://host.docker.internal:9380 */
    private String baseUrl = "http://host.docker.internal:9380";

    /** RAGFlow UI 生成的 API token */
    private String apiKey = "";

    /** 「竞品分析」知识库 ID（UI 创建后复制） */
    private String kbId = "";

    /** 默认 retrieval top-k */
    private int defaultTopK = 10;

    /** 默认相似度阈值 */
    private double similarityThreshold = 0.2;

    /** 关键词匹配权重 (0-1, vector_similarity_weight = 1 - keyword_weight) */
    private double keywordWeight = 0.3;

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;

    /** RAGFlow Chat Assistant 的 dialog_id（即 chat_id），iframe URL 中 shared_id 参数。 */
    private String deepAnalysisSharedId = "";

    /** RAGFlow 嵌入网页的 beta auth token（在 UI Chat → 嵌入网页 → 复制 iframe 中 auth 参数）。 */
    private String deepAnalysisBetaToken = "";

    /** RAGFlow 嵌入类型：chat（Chat Assistant）或 agent（Agent Canvas）。v9.7 默认 agent。 */
    private String deepAnalysisFrom = "agent";

    /** 前端 iframe 用的 RAGFlow UI 地址（与 base-url 不同：UI 在 80 端口，API 在 9380）。 */
    private String uiBaseUrl = "http://localhost";

    /** v9.6 深度对标 Agent 的 canvas id（RAGFlow UI 创建 Agent 后复制） */
    private String deepBenchmarkAgentId = "";

    /** v9.6 单 LLM 兜底 Chat Assistant id（当 Agent 未配置时用） */
    private String deepBenchmarkChatId = "";

    /** v9.6 后续追问用的 Chat Assistant id（轻量场景）— 可与 deepBenchmarkChatId 复用 */
    private String followupChatId = "";
}

