package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.client.RagflowClient;
import com.nevinsight.intelligence.config.BrandConfigProperties;
import com.nevinsight.intelligence.service.QwenAiService;
import com.nevinsight.model.dto.response.CardTextSections;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompetitorAgent implements ReportAgent<CompetitorAgent.Input, CompetitorAgent.Output> {

    private final QwenAiService qwenAiService;
    private final BrandConfigProperties brandConfig;
    private final ObjectMapper objectMapper;

    /** Optional：竞品日报场景才用，舆情日报场景不用。 */
    @Autowired(required = false)
    private RagflowClient ragflowClient;

    private static final String SYSTEM_PROMPT =
            "你是猛士车型品牌的市场分析员。口径说明：猛士是车型品牌/产品品牌，M817、M917 是猛士旗下具体车型。目标是收集并研判互联网竞品信息，"
            + "为猛士的市场投入、产品传播、终端话术和竞争策略提供决策依据。请基于提供的数据进行竞品对标分析，"
            + "禁止编造未在数据中出现的产品信息。";

    private static final String BRIEFING_SYSTEM_PROMPT =
            "你是猛士车型品牌的市场分析员。猛士是车型品牌/产品品牌，M817、M917 是旗下具体车型。根据本品/竞品/行业三类新闻标题，用 1-2 句话写出今日竞品速览，"
            + "只提炼能影响猛士市场判断的信息。"
            + " 要求：(1) 必须提到 1 个具体竞品品牌或车型；(2) 必须涉及 1 个技术维度或销售事件；"
            + " (3) 不编造未给数据；(4) 控制在 80-120 字；(5) 直接输出文本，不要 JSON。";

    /** v6 整体态势分析（不再是事件列表）— 放卡片顶部供洞察用。 */
    private static final String EVENTS_BRIEFING_SYSTEM_PROMPT =
            "你是猛士车型品牌的市场分析员。口径说明：猛士是车型品牌/产品品牌，M817、M917 是猛士旗下具体车型。你的工作是收集互联网上的竞品信息，整理并总结关键信息，"
            + "帮助猛士团队做市场投入、产品传播、终端转化和竞争策略分析。输入是已分类的竞品事件（launch/price_finance/campaign/sales_milestone/strategic_action），"
            + "每条事件含「品牌 - 事件摘要 - URL」。\n"
            + "\n"
            + "**任务**：基于全部输入事件生成「今日竞品动态总结」，覆盖所有高价值竞品动作，不能只挑单一板块。\n"
            + "必须输出 3 行，每行以固定标签开头：\n"
            + "1) 动态总结：用 1-2 句话概括今日最重要的竞品动作，必须点名具体品牌/车型，并覆盖产品、价格、营销、销量中的主要变化。\n"
            + "2) 风险提示：判断这些动作对猛士 M817/917 的潜在影响，如价格挤压、产品节奏抢先、声量分流、销量差距、终端转化压力；没有明显风险也要说明“暂未发现明确负面信号”。\n"
            + "3) 应对策略：给出猛士可执行的响应建议，优先围绕传播重点、终端话术、价格权益、渠道线索、用户反馈、竞品分流监控。\n"
            + "\n"
            + "**风格**：分析师口吻，要有判断，不只是罗列。每行控制在 60-120 字。\n"
            + "**内容边界**：只能分析输入事件里的竞品事实、车型、配置升级、价格权益、营销传播、销量变化及其对猛士的影响。\n"
            + "**信息过滤**：忽略平台账号名单、直播频道名单、纯时间段、图片免责声明、泛化口号和无法用于决策的噪声。\n"
            + "**禁止**：禁止编造未给数据；禁止输出链接；禁止照抄事件清单；禁止使用 JSON；禁止新增其他标题；"
            + "禁止提及 OCR、图片识别、卡片规则、字段名、板块改动、提示词、对话内容、预览说明或任何实现过程。";

    @Override
    public String name() {
        return "COMPETITOR_AGENT";
    }

    /** 兼容现有 DailyReportPipeline 调用：传 3 段原始字符串。 */
    @Override
    public Output execute(Input input) throws AgentExecutionException {
        log.info("[CompetitorAgent] 开始竞品分析 (legacy)");

        StringBuilder prompt = new StringBuilder();
        prompt.append("## 品牌: ").append(brandConfig.getName()).append("\n\n");
        prompt.append("## 竞品数据\n").append(safe(input.getCompetitorData())).append("\n\n");
        prompt.append("## 竞品网络新闻\n").append(safe(input.getCompetitorWebNews())).append("\n\n");
        prompt.append("## 技术参考\n").append(safe(input.getTechReference())).append("\n\n");
        appendBenchmarkRules(prompt);
        prompt.append("\n请返回JSON对象，包含:\n");
        prompt.append("- competitor_tracking: 对象，key为竞品名，value有action/sentiment/marketing/source_url\n");
        prompt.append("- competitor_benchmark: 数组，每项有mengshi_model/competitor_model/benchmark_type/topic/competitor_claim/mengshi_advantage/verdict\n");
        prompt.append("\n必须严格按照对标规则，禁止跨车型对标。返回纯JSON，无markdown包裹。");

        return callLlmAndParse(prompt.toString(), false, Collections.emptyList());
    }

    /**
     * 竞品日报场景：从 RAGFlow 检索 + 已拼好的 SQL 对标表 + 人群清单，
     * 一次性产出 tracking / benchmark / 按人群分组的 talkingPoints。
     */
    public Output runFromKnowledge(LocalDate date,
                                    List<Persona> personas,
                                    String benchmarkTable) {
        if (ragflowClient == null || !ragflowClient.isConfigured()) {
            log.warn("[CompetitorAgent] RAGFlow not configured; runFromKnowledge returning empty");
            return new Output(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
                    "（RAGFlow 未配置，竞品分析降级）");
        }

        String brand = brandConfig.getName();

        // 1. 检索本周竞品动态（话术周表 + 竞品早报）
        List<RagflowClient.RetrievedChunk> dynamics = ragflowClient.retrieve(
                brand + " 本周 竞品 动态 销售话术", 8);

        // 2. 检索我方 USP
        List<RagflowClient.RetrievedChunk> usp = ragflowClient.retrieve(
                brand + " 技术亮点 USP 一级USP 二级USP", 5);

        // 3. 人群画像：按人群编号检索（每人群 1 chunk 即可）
        Map<String, String> personaContext = new LinkedHashMap<>();
        for (Persona p : personas) {
            String q = "客户人群 " + p.code + " " + p.name + " 痛点 核心关注";
            List<RagflowClient.RetrievedChunk> chunks = ragflowClient.retrieve(q, 1);
            personaContext.put(p.code, formatChunks(chunks));
        }

        // 拼 prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 品牌: ").append(brand).append("\n## 日期: ").append(date).append("\n\n");

        prompt.append("## 本周竞品动态（向量检索片段，请仅基于这些数据分析）\n");
        prompt.append(formatChunks(dynamics)).append("\n\n");

        prompt.append("## 我方 USP / 技术亮点\n");
        prompt.append(formatChunks(usp)).append("\n\n");

        prompt.append("## 技术对标表（来自 autohome_spec，精确数值）\n");
        prompt.append(safe(benchmarkTable)).append("\n\n");

        prompt.append("## 目标客户人群\n");
        for (Persona p : personas) {
            prompt.append("- **").append(p.code).append(" ").append(p.name).append("**:\n");
            prompt.append("  画像: ").append(personaContext.getOrDefault(p.code, "（无）")).append("\n");
        }

        appendBenchmarkRules(prompt);

        prompt.append("\n## 输出要求\n");
        prompt.append("返回纯 JSON（无 markdown），包含以下字段：\n");
        prompt.append("1. briefing: 字符串，1-2 句话概括今日竞品态势\n");
        prompt.append("2. competitor_tracking: 对象，key为竞品名，value 含 action/sentiment/marketing/source_url\n");
        prompt.append("3. competitor_benchmark: 数组，每项含 mengshi_model/competitor_model/benchmark_type/topic/competitor_claim/mengshi_advantage/verdict\n");
        prompt.append("4. talking_points: 数组。**针对每个人群（P01-P").append(String.format("%02d", personas.size()))
              .append("）至少产出 1-2 条话术**。每项含：\n");
        prompt.append("   - persona: 人群编号（如 P01）\n");
        prompt.append("   - persona_name: 人群名称\n");
        prompt.append("   - scenario: 具体销售场景，如 '当客户说 XXX 时'\n");
        prompt.append("   - talking_point: 话术内容，要 (a) 命中该人群核心关注；(b) 引用我方 USP 或对标表精确数据；(c) 避免攻击竞品\n");
        prompt.append("   - mengshi_model: 涉及的我方车型\n");
        prompt.append("   - competitor_model: 涉及的对手车型\n\n");
        prompt.append("严禁跨车型对标，严禁编造数据。\n");

        return callLlmAndParse(prompt.toString(), true, personas);
    }

    /** 通义千问 qwen-max 上下文上限 30720 token ≈ 20000 中文字符 (含 system + output 预留)。
     *  prompt 留 16000 char 给输入。 */
    private static final int PROMPT_HARD_CAP = 16000;

    private Output callLlmAndParse(String userPrompt, boolean expectTalkingPoints, List<Persona> personas) {
        if (userPrompt.length() > PROMPT_HARD_CAP) {
            log.warn("[CompetitorAgent] prompt {} chars exceeds cap {}; truncating",
                    userPrompt.length(), PROMPT_HARD_CAP);
            userPrompt = userPrompt.substring(0, PROMPT_HARD_CAP)
                    + "\n\n…（输入过长已截断，请基于已给数据作答）";
        }
        log.info("[CompetitorAgent] prompt size {} chars", userPrompt.length());
        try {
            // 走 OpenAI 兼容 HTTP 端点，规避 DashScope SDK 在容器内的长连接断流问题
            String resp = qwenAiService.chatWithReportModelHttp(SYSTEM_PROMPT, userPrompt);
            resp = cleanJson(resp);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(resp, Map.class);

            Map<String, CardTextSections.CompetitorTracking> tracking = Collections.emptyMap();
            List<CardTextSections.CompetitorBenchmark> benchmarks = Collections.emptyList();
            List<CardTextSections.SuggestedTalkingPoint> talkingPoints = Collections.emptyList();
            String briefing = String.valueOf(parsed.getOrDefault("briefing", ""));

            if (parsed.containsKey("competitor_tracking")) {
                tracking = objectMapper.convertValue(parsed.get("competitor_tracking"),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class,
                                CardTextSections.CompetitorTracking.class));
            }
            if (parsed.containsKey("competitor_benchmark")) {
                benchmarks = objectMapper.convertValue(parsed.get("competitor_benchmark"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                CardTextSections.CompetitorBenchmark.class));
            }
            if (expectTalkingPoints && parsed.containsKey("talking_points")) {
                talkingPoints = objectMapper.convertValue(parsed.get("talking_points"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                CardTextSections.SuggestedTalkingPoint.class));
                // 兜底填 personaName（LLM 可能漏）
                Map<String, String> codeToName = personas.stream()
                        .collect(Collectors.toMap(p -> p.code, p -> p.name, (a, b) -> a));
                for (CardTextSections.SuggestedTalkingPoint tp : talkingPoints) {
                    if (tp.getPersonaName() == null || tp.getPersonaName().isEmpty()) {
                        tp.setPersonaName(codeToName.getOrDefault(tp.getPersona(), ""));
                    }
                }
            }
            return new Output(tracking, benchmarks, talkingPoints, briefing);
        } catch (Exception e) {
            log.error("[CompetitorAgent] 执行失败: {}", e.getMessage());
            return new Output(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(),
                    "（LLM 调用失败：" + e.getMessage() + "）");
        }
    }

    private void appendBenchmarkRules(StringBuilder sb) {
        if (brandConfig.getModelBenchmarkRules() == null || brandConfig.getModelBenchmarkRules().isEmpty()) {
            return;
        }
        sb.append("## 猛士竞品对标规则\n");
        for (Map.Entry<String, BrandConfigProperties.ModelBenchmarkRule> entry :
                brandConfig.getModelBenchmarkRules().entrySet()) {
            sb.append("- ").append(entry.getKey()).append(":\n");
            if (entry.getValue().getCore() != null) {
                sb.append("  核心竞品: ").append(String.join(", ", entry.getValue().getCore())).append("\n");
            }
            if (entry.getValue().getOpportunity() != null) {
                sb.append("  机会竞品: ").append(String.join(", ", entry.getValue().getOpportunity())).append("\n");
            }
        }
    }

    /** 单 chunk 最多保留 600 字（避免一次 retrieve 把 prompt 撑爆 LLM token 上限）。 */
    private static final int CHUNK_TRUNCATE = 600;

    private String formatChunks(List<RagflowClient.RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return "（无相关数据）";
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RagflowClient.RetrievedChunk c : chunks) {
            String content = safe(c.content);
            if (content.length() > CHUNK_TRUNCATE) {
                content = content.substring(0, CHUNK_TRUNCATE) + "…";
            }
            sb.append(i++).append(". [").append(safe(c.docName)).append("] ")
              .append(content).append("\n");
        }
        return sb.toString();
    }

    /**
     * v2 入口：仅做今日总结（briefing）。输入是已分类的 32h 新闻列表。
     * 不再让 LLM 编造 tracking / benchmark / talkingPoints — 那些都用 SQL 数据。
     *
     * @param selfNewsTitles   本品新闻标题列表（按时间倒序）
     * @param competitorBuckets  竞品新闻 brand -> List<title>
     * @param industryNewsTitles 行业新闻标题列表
     * @return 1-2 句速览，失败返空串
     */
    public String summarizeBriefing(List<String> selfNewsTitles,
                                     Map<String, List<String>> competitorBuckets,
                                     List<String> industryNewsTitles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("## 数据窗口：过去 32 小时\n\n");

        prompt.append("## 本品 ").append(brandConfig.getName()).append(" 相关新闻 (")
              .append(selfNewsTitles.size()).append(" 条)\n");
        appendTitles(prompt, selfNewsTitles, 10);

        prompt.append("\n## 竞品新闻 (按品牌)\n");
        if (competitorBuckets.isEmpty()) {
            prompt.append("（无）\n");
        } else {
            competitorBuckets.forEach((brand, titles) -> {
                prompt.append("- ").append(brand).append(" (").append(titles.size()).append(" 条)：\n");
                appendTitles(prompt, titles, 4);
            });
        }

        prompt.append("\n## 新能源行业新闻 (")
              .append(industryNewsTitles.size()).append(" 条)\n");
        appendTitles(prompt, industryNewsTitles, 6);

        prompt.append("\n请按系统要求输出 1-2 句速览。");

        try {
            String resp = qwenAiService.chatWithReportModelHttp(BRIEFING_SYSTEM_PROMPT, prompt.toString());
            return resp == null ? "" : resp.trim();
        } catch (Exception e) {
            log.error("[CompetitorAgent] summarizeBriefing failed: {}", e.getMessage());
            return "";
        }
    }

    /**
     * v3 速览：用已分类的 event_summary 喂 LLM，按主要事件板块输出。
     * @param byEvent  event_type → 该类事件列表（每条至少 brand_name + event_summary）
     */
    public String summarizeEvents(java.util.Map<String, java.util.List<com.nevinsight.model.entity.core.WebSearchNews>> byEvent) {
        StringBuilder prompt = new StringBuilder("## 32h 内已分类竞品事件\n\n");
        String[][] sections = {
                {"launch",          "🚀 产品动态"},
                {"price_finance",   "💰 价格 & 金融政策"},
                {"campaign",        "📣 营销传播"},
                {"sales_milestone", "📈 销量 & 交付里程碑"},
                {"strategic_action","🧭 战略动作"}
        };
        for (String[] sec : sections) {
            String key = sec[0];
            String label = sec[1];
            java.util.List<com.nevinsight.model.entity.core.WebSearchNews> rows =
                    byEvent.getOrDefault(key, java.util.Collections.emptyList());
            prompt.append("### ").append(label).append(" (").append(rows.size()).append(" 条)\n");
            if (rows.isEmpty()) {
                prompt.append("（暂无）\n\n");
                continue;
            }
            int n = Math.min(rows.size(), 6);
            for (int i = 0; i < n; i++) {
                com.nevinsight.model.entity.core.WebSearchNews x = rows.get(i);
                String summary = x.getEventSummary();
                if (summary == null || summary.isEmpty()) summary = x.getTitle();
                String models = x.getModelMentioned();
                prompt.append("  - [").append(safe(x.getBrandName())).append("] ")
                      .append(safe(summary));
                if (models != null && !models.isEmpty()) {
                    prompt.append(" (").append(models).append(")");
                }
                // 关键：附 URL 给 LLM 用作来源链接
                if (x.getUrl() != null && !x.getUrl().isEmpty()) {
                    prompt.append(" — URL: ").append(x.getUrl());
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        prompt.append("请按系统要求输出动态总结、风险提示、应对策略三行。");
        try {
            String resp = qwenAiService.chatWithReportModelHttp(EVENTS_BRIEFING_SYSTEM_PROMPT, prompt.toString());
            return resp == null ? "" : resp.trim();
        } catch (Exception e) {
            log.error("[CompetitorAgent] summarizeEvents failed: {}", e.getMessage());
            return "";
        }
    }

    /** 拆出：仅检索 RAGFlow 生成销售话术（v1 复用），不再附带 tracking/benchmark。 */
    public List<CardTextSections.SuggestedTalkingPoint> runTalkingPointsOnly(LocalDate date,
                                                                              List<Persona> personas) {
        if (ragflowClient == null || !ragflowClient.isConfigured()) {
            log.warn("[CompetitorAgent] RAGFlow not configured; skip talkingPoints");
            return Collections.emptyList();
        }
        String brand = brandConfig.getName();

        List<RagflowClient.RetrievedChunk> usp = ragflowClient.retrieve(
                brand + " 技术亮点 USP 全栈华为 全维安全", 5);

        Map<String, String> personaContext = new LinkedHashMap<>();
        for (Persona p : personas) {
            List<RagflowClient.RetrievedChunk> chunks = ragflowClient.retrieve(
                    "客户人群 " + p.code + " " + p.name + " 痛点 核心关注", 1);
            personaContext.put(p.code, formatChunks(chunks));
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("## 品牌: ").append(brand).append("\n");
        prompt.append("## 我方 USP\n").append(formatChunks(usp)).append("\n");
        prompt.append("## 目标客户人群\n");
        for (Persona p : personas) {
            prompt.append("- **").append(p.code).append(" ").append(p.name).append("**:\n");
            prompt.append("  画像: ").append(personaContext.getOrDefault(p.code, "（无）")).append("\n");
        }
        prompt.append("\n## 输出要求\n");
        prompt.append("返回 JSON：{ \"talking_points\": [{persona, persona_name, scenario, talking_point, competitor_model}] }\n");
        prompt.append("**针对每个人群产出 1-2 条话术**。命中该人群核心关注，引用 USP，避免攻击竞品。\n");

        try {
            String resp = qwenAiService.chatWithReportModelHttp(SYSTEM_PROMPT, prompt.toString());
            resp = cleanJson(resp);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(resp, Map.class);
            if (!parsed.containsKey("talking_points")) return Collections.emptyList();

            List<CardTextSections.SuggestedTalkingPoint> tps = objectMapper.convertValue(
                    parsed.get("talking_points"),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, CardTextSections.SuggestedTalkingPoint.class));
            // 兜底 personaName
            Map<String, String> codeToName = personas.stream()
                    .collect(Collectors.toMap(p -> p.code, p -> p.name, (a, b) -> a));
            for (CardTextSections.SuggestedTalkingPoint tp : tps) {
                if (tp.getPersonaName() == null || tp.getPersonaName().isEmpty()) {
                    tp.setPersonaName(codeToName.getOrDefault(tp.getPersona(), ""));
                }
            }
            return tps;
        } catch (Exception e) {
            log.error("[CompetitorAgent] runTalkingPointsOnly failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void appendTitles(StringBuilder sb, List<String> titles, int max) {
        int n = Math.min(titles.size(), max);
        for (int i = 0; i < n; i++) {
            sb.append("  ").append(i + 1).append(". ").append(safe(titles.get(i))).append("\n");
        }
        if (titles.size() > max) {
            sb.append("  ...（共 ").append(titles.size()).append(" 条，已截断）\n");
        }
    }

    @Override
    public Output fallback(Input input, Exception cause) {
        log.warn("[CompetitorAgent] 降级: {}", cause.getMessage());
        return new Output(Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyList(), "（降级模式：无竞品分析）");
    }

    private String cleanJson(String raw) {
        raw = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        raw = raw.replaceAll(",\\s*([}\\]])", "$1");
        return raw;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @Data
    public static class Input {
        private String competitorData;
        private String competitorWebNews;
        private String techReference;

        public Input(String competitorData, String competitorWebNews, String techReference) {
            this.competitorData = competitorData;
            this.competitorWebNews = competitorWebNews;
            this.techReference = techReference;
        }
    }

    @Data
    public static class Output {
        private Map<String, CardTextSections.CompetitorTracking> competitorTracking;
        private List<CardTextSections.CompetitorBenchmark> competitorBenchmark;
        private List<CardTextSections.SuggestedTalkingPoint> talkingPoints;
        private String briefing;

        /** 兼容旧调用：旧 Output(tracking, benchmark)。 */
        public Output(Map<String, CardTextSections.CompetitorTracking> tracking,
                      List<CardTextSections.CompetitorBenchmark> benchmark) {
            this(tracking, benchmark, Collections.emptyList(), "");
        }

        public Output(Map<String, CardTextSections.CompetitorTracking> tracking,
                      List<CardTextSections.CompetitorBenchmark> benchmark,
                      List<CardTextSections.SuggestedTalkingPoint> talkingPoints,
                      String briefing) {
            this.competitorTracking = tracking;
            this.competitorBenchmark = benchmark;
            this.talkingPoints = talkingPoints;
            this.briefing = briefing;
        }
    }

    /** 简单的人群 DTO（不入 DB，传参用）。 */
    @Data
    public static class Persona {
        public final String code;   // P01
        public final String name;   // 硬派越野玩家
        public Persona(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
