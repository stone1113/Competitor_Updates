package com.nevinsight.intelligence.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.intelligence.dto.AnalystResult;
import com.nevinsight.intelligence.dto.BenchmarkContext;
import com.nevinsight.intelligence.dto.BenchmarkContext.ModelData;
import com.nevinsight.intelligence.dto.BenchmarkContext.SalesSnapshot;
import com.nevinsight.intelligence.service.QwenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * v9 深度对标 Agent — 5 个 Analyst + 1 个 Synthesizer。
 *
 * 设计原则（手写 Java 编排，复用 QwenAiService.chatWithReportModelHttp）：
 *   - 5 个 Analyst 共用 JSON 输出 schema：{dimension, winner, scores, key_findings}
 *   - Synthesizer 输入 5 个 JSON → 输出 Markdown 报告
 *   - 每 prompt 加固「严格 JSON / 不含 markdown 包裹 / 必须含具体数字」
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepBenchmarkAgent {

    /** 5 个分析维度（key 用于 SSE 事件 step 名） */
    public enum Dimension {
        POWER("动力性能", "⚡", List.of(
                "最大功率", "最大扭矩", "最大马力", "电动机总功率", "电动机总扭矩",
                "电动机总马力", "后电动机最大功率", "前电动机最大功率",
                "官方0-100km/h加速", "官方0-50km/h加速",
                "WLTC综合油耗", "NEDC综合油耗", "百公里耗电量",
                "NEDC纯电续航里程", "CLTC纯电续航", "WLTC纯电续航",
                "电池能量", "电池能量密度", "电池类型", "快充功能",
                "电池充电时间", "电池荷电状态范围", "对外放电功率",
                "能源类型", "驱动方式", "驱动电机数")),
        BODY("车身空间", "🚗", List.of(
                "长*宽*高", "高度", "前轮距", "车身结构", "车门开启方式",
                "整备质量", "最大满载质量", "最大载重质量", "准拖挂车总质量",
                "后备厢容积", "油箱容积", "车门数", "座位数",
                "最高车速", "上市时间", "厂商", "厂商指导价", "级别")),
        OFFROAD("越野通过性", "🛞", List.of(
                "接近角", "离去角", "纵向通过角", "最大涉水深度",
                "最大爬坡度", "最大爬坡角度", "最小离地间隙", "满载最小离地间隙",
                "最小转弯半径", "前悬挂类型", "后悬挂类型", "车体结构",
                "悬挂结构", "前制动类型", "后制动类型", "驻车制动器类型",
                "备胎放置方式", "备用轮胎规格")),
        PRICE("价格价值", "💰", List.of(
                "厂商指导价", "上市时间", "厂商", "首任车主质保政策",
                "整车质保", "电池组质保", "三电系统质保", "三电首任车主质保政策")),
        SALES("销量市场", "📈", List.of());

        public final String cn;
        public final String icon;
        /** 该维度关注的参数名（白名单，给 prompt 当上下文） */
        public final List<String> params;

        Dimension(String cn, String icon, List<String> params) {
            this.cn = cn;
            this.icon = icon;
            this.params = params;
        }
    }

    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    /**
     * 单个分析师调用 — 给定维度 + ctx → AnalystResult。
     */
    public AnalystResult analyze(Dimension dim, BenchmarkContext ctx) {
        long start = System.currentTimeMillis();
        AnalystResult result = new AnalystResult();
        result.setDimension(dim.cn);
        try {
            String systemPrompt = buildAnalystSystemPrompt(dim);
            String userPrompt = buildAnalystUserPrompt(dim, ctx);
            String resp = qwenAiService.chatWithReportModelHttp(systemPrompt, userPrompt);
            parseAnalystResponse(resp, result, ctx);
        } catch (Exception e) {
            log.error("[DeepBenchmark] {} analyst failed: {}", dim.name(), e.getMessage(), e);
            result.setError(e.getMessage());
        } finally {
            result.setElapsedMs(System.currentTimeMillis() - start);
            log.info("[DeepBenchmark] {} 完成 winner={} elapsed={}ms",
                    dim.name(), result.getWinner(), result.getElapsedMs());
        }
        return result;
    }

    /**
     * Synthesizer — 综合 5 个 analyst 结果，输出 Markdown 报告。
     */
    public String synthesize(BenchmarkContext ctx, Map<Dimension, AnalystResult> analystResults) {
        long start = System.currentTimeMillis();
        try {
            String systemPrompt = buildSynthesizerSystemPrompt();
            String userPrompt = buildSynthesizerUserPrompt(ctx, analystResults);
            String resp = qwenAiService.chatWithReportModelHttp(systemPrompt, userPrompt);
            String md = stripMarkdownFence(resp);
            log.info("[DeepBenchmark] Synthesizer 完成 length={} elapsed={}ms",
                    md.length(), System.currentTimeMillis() - start);
            return md;
        } catch (Exception e) {
            log.error("[DeepBenchmark] Synthesizer 失败: {}", e.getMessage(), e);
            return "# 综合报告生成失败\n\n错误: " + e.getMessage();
        }
    }

    // ===== Prompt 构造 =====

    private String buildAnalystSystemPrompt(Dimension dim) {
        return "你是「" + dim.cn + "分析师」，专门评估新能源汽车的【" + dim.cn + "】维度。\n\n" +
                "【任务】\n" +
                "1. 仔细对比所有车型在 " + dim.cn + " 维度的关键参数 / 数据\n" +
                "2. 给每个车型 0-10 分打分（10 = 同类最佳）\n" +
                "3. 选出该维度胜者\n" +
                "4. 输出 3-5 句关键发现，每句必须含具体数字（如「猛士M817 最大功率 200kW，比豹5 320kW 落后 37.5%」）\n\n" +
                "【严格输出格式 — 纯 JSON，无 markdown 代码块包裹，无说明文字】\n" +
                "{\n" +
                "  \"dimension\": \"" + dim.cn + "\",\n" +
                "  \"winner\": \"<车型名>\",\n" +
                "  \"scores\": {\"<车型名>\": <0-10整数>, ...},\n" +
                "  \"key_findings\": [\n" +
                "    \"<含具体数字的对比发现>\",\n" +
                "    ...\n" +
                "  ]\n" +
                "}\n\n" +
                "【约束】\n" +
                "- 不要捏造数据，只用 USER 提供的事实\n" +
                "- 数据缺失就在 findings 中标注「<车型> 该参数缺失」\n" +
                "- key_findings 必须 ≥ 3 条，每条 ≤ 100 字";
    }

    private String buildAnalystUserPrompt(Dimension dim, BenchmarkContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("【对标场景】\n");
        sb.append("本品：").append(ctx.getSelfModel());
        if (ctx.getSelfClass() != null) sb.append("（").append(ctx.getSelfClass()).append("）");
        sb.append("\n竞品：");
        for (int i = 1; i < ctx.getAllModels().size(); i++) {
            if (i > 1) sb.append(" / ");
            sb.append(ctx.getAllModels().get(i));
        }
        sb.append("\n\n");

        sb.append("【").append(dim.cn).append(" 维度数据】\n");

        if (dim == Dimension.SALES) {
            sb.append(buildSalesTable(ctx));
        } else {
            sb.append(buildParamTable(dim, ctx));
            if (dim == Dimension.PRICE) {
                // 价格维度额外带 36h 价格事件
                sb.append("\n【近 36h 官号价格金融事件】\n");
                for (String model : ctx.getAllModels()) {
                    ModelData md = ctx.getModelDataMap().get(model);
                    if (md == null || md.getPriceEvents().isEmpty()) continue;
                    sb.append(model).append("：\n");
                    for (String e : md.getPriceEvents()) {
                        sb.append("  - ").append(e).append("\n");
                    }
                }
            }
        }

        sb.append("\n【任务】按 system 指令分析上述数据，返回 JSON。");
        return sb.toString();
    }

    private String buildParamTable(Dimension dim, BenchmarkContext ctx) {
        StringBuilder sb = new StringBuilder();
        // 表头
        sb.append("| 参数 |");
        for (String m : ctx.getAllModels()) sb.append(" ").append(m).append(" |");
        sb.append("\n|---|");
        for (int i = 0; i < ctx.getAllModels().size(); i++) sb.append("---|");
        sb.append("\n");
        // 每行一个关注参数
        for (String paramKey : dim.params) {
            // 跨车型有任意值就显示这行
            boolean anyValue = ctx.getModelDataMap().values().stream()
                    .anyMatch(md -> findMatchingParam(md.getSpecs(), paramKey) != null);
            if (!anyValue) continue;
            sb.append("| ").append(paramKey).append(" |");
            for (String m : ctx.getAllModels()) {
                ModelData md = ctx.getModelDataMap().get(m);
                String v = md == null ? null : findMatchingParam(md.getSpecs(), paramKey);
                sb.append(" ").append(v == null ? "—" : v).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** 参数名模糊匹配（如 paramKey="最大功率" 匹配「最大功率(kW)」/ 「电动机总功率(kW)」） */
    private String findMatchingParam(Map<String, String> specs, String paramKey) {
        if (specs.containsKey(paramKey)) return specs.get(paramKey);
        for (Map.Entry<String, String> e : specs.entrySet()) {
            if (e.getKey() != null && e.getKey().contains(paramKey)) return e.getValue();
        }
        return null;
    }

    private String buildSalesTable(BenchmarkContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 车型 | 最新月销 | 上月销 | 上上月 | 本年累计 |\n");
        sb.append("|---|---:|---:|---:|---:|\n");
        for (String m : ctx.getAllModels()) {
            ModelData md = ctx.getModelDataMap().get(m);
            sb.append("| ").append(m).append(" |");
            if (md == null || md.getRecentSales().isEmpty()) {
                sb.append(" — | — | — | — |\n");
                continue;
            }
            List<SalesSnapshot> sales = md.getRecentSales();
            for (int i = 0; i < 3; i++) {
                if (i < sales.size() && sales.get(i).getSalesCount() != null) {
                    sb.append(" ").append(sales.get(i).getSalesCount()).append(" |");
                } else {
                    sb.append(" — |");
                }
            }
            Integer ytd = sales.get(0).getYtdCount();
            sb.append(" ").append(ytd == null ? "—" : ytd).append(" |\n");
        }
        return sb.toString();
    }

    private String buildSynthesizerSystemPrompt() {
        return "你是首席汽车产品分析师，综合 5 位分析师的报告，输出**图文并茂**的 Markdown 深度对标。\n\n" +
                "【严格 Markdown 格式（无外层代码块包裹，直接输出 markdown 文本）】\n" +
                "# {本品} vs {N} 款竞品 深度对标报告\n" +
                "> 报告时间：{今日}\n\n" +
                "## 一、执行摘要\n" +
                "{200 字内综合论断 — 本品在哪强、哪弱、整体定位}\n\n" +
                "## 二、综合评分（含雷达图）\n\n" +
                "```chart\n" +
                "{\"type\":\"radar\",\"data\":{\"labels\":[\"动力\",\"车身\",\"越野\",\"价格\",\"销量\"],\"datasets\":[" +
                "{\"label\":\"{本品名}\",\"data\":[score1,score2,score3,score4,score5]}," +
                "{\"label\":\"{竞品1名}\",\"data\":[...,...,...,...,...]}" +
                "]}}\n" +
                "```\n\n" +
                "## 三、维度对比\n" +
                "### ⚡ 动力性能\n" +
                "- **胜者**：{winner}\n" +
                "- **评分**：本品 X/10  竞品 Y/10\n" +
                "- **关键发现**：（保留分析师 findings 原文，每条一行）\n\n" +
                "### 🚗 车身空间\n### 🛞 越野通过性\n### 💰 价格价值\n### 📈 销量市场\n\n" +
                "## 四、近 3 月销量趋势\n（仅当 SALES analyst 提到具体月份数字时输出）\n\n" +
                "```chart\n" +
                "{\"type\":\"line\",\"data\":{\"labels\":[\"2/2026\",\"3/2026\",\"4/2026\"],\"datasets\":[" +
                "{\"label\":\"{本品}\",\"data\":[800,850,866]}," +
                "{\"label\":\"{竞品1}\",\"data\":[2489,4174,5511]}" +
                "]}}\n" +
                "```\n\n" +
                "## 五、综合胜负表\n" +
                "| 车型 | 动力 | 车身 | 越野 | 价格 | 销量 | 总分 |\n" +
                "|---|---:|---:|---:|---:|---:|---:|\n" +
                "| **{本品}** | ... | ... |\n" +
                "（按总分降序；本品行加粗）\n\n" +
                "## 六、战术建议（针对本品）\n" +
                "1. （含维度 + 数字 + 可操作动作）\n" +
                "2. ...\n" +
                "（3-5 条）\n\n" +
                "## 七、风险提示\n" +
                "- ...\n\n" +
                "【约束】\n" +
                "- 雷达图 chart 必须输出（5 维度 = 5 个 dataset）；销量 chart 仅在数据齐时输出\n" +
                "- chart 代码块必须是合法 JSON：{\"type\":\"radar|line|bar\",\"data\":{\"labels\":[],\"datasets\":[]}}\n" +
                "- 不要捏造，只引用 5 分析师给的数据；战术建议必须可操作\n" +
                "- 不要输出外层 ```markdown 围栏\n";
    }

    private String buildSynthesizerUserPrompt(BenchmarkContext ctx, Map<Dimension, AnalystResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("【对标场景】\n");
        sb.append("本品：").append(ctx.getSelfModel());
        if (ctx.getSelfClass() != null) sb.append("（").append(ctx.getSelfClass()).append("）");
        sb.append("\n竞品：");
        for (int i = 1; i < ctx.getAllModels().size(); i++) {
            if (i > 1) sb.append(" / ");
            sb.append(ctx.getAllModels().get(i));
        }
        sb.append("\n\n");

        sb.append("【5 位分析师的 JSON 结果】\n");
        for (Map.Entry<Dimension, AnalystResult> e : results.entrySet()) {
            AnalystResult r = e.getValue();
            sb.append("\n--- ").append(e.getKey().icon).append(" ").append(e.getKey().cn).append(" ---\n");
            try {
                sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(r));
            } catch (Exception ex) {
                sb.append(r);
            }
            sb.append("\n");
        }
        sb.append("\n【任务】按 system 指令输出 Markdown 报告。今日：")
          .append(java.time.LocalDate.now()).append("。");
        return sb.toString();
    }

    // ===== 解析 =====

    private void parseAnalystResponse(String resp, AnalystResult result, BenchmarkContext ctx) {
        if (resp == null || resp.isEmpty()) {
            result.setError("LLM 空响应");
            return;
        }
        String cleaned = resp.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();
        // 截 JSON
        int s = cleaned.indexOf('{');
        int e = cleaned.lastIndexOf('}');
        if (s >= 0 && e > s) cleaned = cleaned.substring(s, e + 1);

        try {
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.has("winner")) result.setWinner(node.path("winner").asText(""));
            if (node.has("scores")) {
                Map<String, Integer> scores = new LinkedHashMap<>();
                node.path("scores").fields().forEachRemaining(en -> {
                    int v = en.getValue().asInt(0);
                    scores.put(en.getKey(), Math.max(0, Math.min(10, v)));
                });
                result.setScores(scores);
            }
            if (node.has("key_findings")) {
                List<String> findings = new ArrayList<>();
                node.path("key_findings").elements().forEachRemaining(n -> {
                    String t = n.asText("").trim();
                    if (!t.isEmpty()) findings.add(t);
                });
                result.setKeyFindings(findings);
            }
        } catch (Exception ex) {
            log.warn("[DeepBenchmark] 解析 analyst 响应失败: {} (resp[0..200]={})",
                    ex.getMessage(), cleaned.substring(0, Math.min(200, cleaned.length())));
            result.setError("JSON 解析失败: " + ex.getMessage());
        }
    }

    private String stripMarkdownFence(String s) {
        if (s == null) return "";
        return s.trim()
                .replaceAll("^```markdown\\s*", "")
                .replaceAll("^```md\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();
    }
}
