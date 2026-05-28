package com.nevinsight.intelligence.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v8 OCR 增强：扫官号产品/价格/营销候选（有图未跑过 OCR），调通义千问视觉模型提取图片文字 +
 * 抓配置参数、价格权益、营销传播机制等关键信息，写回 image_ocr_text + 提权 event_summary + event_importance。
 *
 * 触发条件（mapper：findOcrPending）：
 *   - event_type IN ('price_finance', 'launch', 'campaign')
 *   - source_tool LIKE '%_official'
 *   - image_urls 非空
 *   - ocr_ts IS NULL（未跑过）
 *
 * 失败/无政策也写 ocr_ts，避免重跑同一条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrEnrichmentService {

    /** 单帖最多取前 N 张图（控成本，4 张通常覆盖海报关键信息） */
    private static final int MAX_IMAGES_PER_POST = 4;

    /** 默认单次最多处理多少条 */
    public static final int DEFAULT_LIMIT = 20;

    private static final String SYSTEM_PROMPT =
            "你是猛士车型品牌的市场分析员，正在从竞品官号海报中提取可用于市场决策和终端分析的信息。口径说明：猛士是车型品牌/产品品牌，M817、M917 是猛士旗下具体车型。"
            + "下列图片是车企官博发的上市公告 / 优惠政策 / 营销传播 / 试驾传播海报。请：\n" +
            "1. 提取图片中所有可见文字（OCR）\n" +
            "2. 重点抓三类关键信息：\n" +
            "   - 产品配置/参数/技术：车款名称、轴距、续航、动力、扭矩、智驾、底盘、座舱、四驱、涉水、选装/标配\n" +
            "   - 价格金融：上市定价、限时价、优惠金额、首付比例、0 息周期、置换补贴、保险/租赁优惠\n" +
            "   - 营销传播：活动名称、时间地点、参与门槛、试驾/露营/赛事场景、赠品/权益、报名方式、代言/KOL/联名/节日传播\n" +
            "   - 忽略无决策价值的文字：直播平台/账号名单、纯时间段、二维码提示、图片免责声明、排名不分先后、泛化口号。\n" +
            "3. **严格返回 JSON**（无 markdown、无说明）：\n" +
            "{\n" +
            "  \"ocr_text\": \"所有提取的文字（按自然阅读顺序拼接）\",\n" +
            "  \"has_finance_policy\": true/false (含上市定价或任何价格/优惠信息均为 true),\n" +
            "  \"policy_summary\": \"≤120 字的市场分析摘要；只写对猛士有参考价值的具体产品/价格/营销信息，必须保留具体数字、配置名、技术名、权益金额、活动时间/地点/规则；禁止写'等产品信息/价格金融信息/活动机制'这类泛化概括；只有平台账号/免责声明/纯时间等噪声则空串\"\n" +
            "}";

    private final WebSearchNewsMapper newsMapper;
    private final QwenAiService qwenAiService;
    private final ObjectMapper objectMapper;

    public Result run(int limit) {
        if (limit <= 0) limit = DEFAULT_LIMIT;
        List<WebSearchNews> pending = newsMapper.findOcrPending(limit);
        if (pending.isEmpty()) {
            log.info("[OcrEnrichment] no pending");
            return new Result(0, 0, 0);
        }
        log.info("[OcrEnrichment] start pending={}", pending.size());

        int processed = 0;
        int policyHit = 0;
        int failed = 0;
        for (WebSearchNews n : pending) {
            processed++;
            try {
                if (enrichOne(n)) policyHit++;
            } catch (Exception e) {
                failed++;
                log.warn("[OcrEnrichment] id={} failed: {}", n.getId(), e.getMessage());
                // 失败也标 ocr_ts 避免重试爆掉
                try { newsMapper.markOcrDone(n.getId(), System.currentTimeMillis()); } catch (Exception ignored) {}
            }
        }
        log.info("[OcrEnrichment] done processed={} policy_hit={} failed={}",
                processed, policyHit, failed);
        return new Result(processed, policyHit, failed);
    }

    /** @return true 若识到优惠政策 */
    private boolean enrichOne(WebSearchNews n) {
        List<String> imageUrls = parseImageUrls(n.getImageUrls());
        if (imageUrls.isEmpty()) {
            newsMapper.markOcrDone(n.getId(), System.currentTimeMillis());
            return false;
        }
        String userText = buildUserText(n);
        String resp;
        try {
            resp = qwenAiService.chatWithVisionModelHttp(SYSTEM_PROMPT, userText, imageUrls);
        } catch (Exception e) {
            log.warn("[OcrEnrichment] LLM 调用失败 id={}: {}", n.getId(), e.getMessage());
            newsMapper.markOcrDone(n.getId(), System.currentTimeMillis());
            return false;
        }

        // 解析 JSON
        String ocrText = "";
        boolean hasPolicy = false;
        String policySummary = "";
        try {
            String cleaned = resp == null ? "" : resp.trim()
                    .replaceAll("^```json\\s*", "")
                    .replaceAll("^```\\s*", "")
                    .replaceAll("```\\s*$", "")
                    .trim();
            // 截取首尾大括号
            int s = cleaned.indexOf('{');
            int e = cleaned.lastIndexOf('}');
            if (s >= 0 && e > s) cleaned = cleaned.substring(s, e + 1);
            JsonNode node = objectMapper.readTree(cleaned);
            ocrText = node.path("ocr_text").asText("");
            hasPolicy = node.path("has_finance_policy").asBoolean(false);
            policySummary = node.path("policy_summary").asText("");
        } catch (Exception e) {
            log.warn("[OcrEnrichment] 解析失败 id={} resp={}", n.getId(),
                    resp == null ? "" : resp.substring(0, Math.min(200, resp.length())));
            newsMapper.markOcrDone(n.getId(), System.currentTimeMillis());
            return false;
        }

        // 写回
        String combinedOcr = ocrText;
        boolean hasImageSummary = policySummary != null && !policySummary.isEmpty();
        if (hasImageSummary) {
            combinedOcr = ocrText + "\n【图摘】" + policySummary;
        }
        // 提权 + 增强 event_summary
        String newSummary = n.getEventSummary() == null ? "" : n.getEventSummary();
        Integer newImportance = n.getEventImportance();
        if (hasImageSummary) {
            // 把图片摘要追加到 summary，但避免重复追加（idempotent）
            if (!newSummary.contains("图含：")) {
                newSummary = (newSummary.isEmpty() ? "" : newSummary + " / ") + "图含：" + policySummary;
            }
            int base = newImportance == null ? 5 : newImportance;
            newImportance = Math.min(10, base + 2);
        }

        newsMapper.updateOcrFields(n.getId(), combinedOcr, newSummary, newImportance,
                System.currentTimeMillis());
        log.info("[OcrEnrichment] ✓ id={} brand={} policy={} summary={}",
                n.getId(), n.getBrandName(), hasPolicy,
                newSummary.length() > 60 ? newSummary.substring(0, 60) + "…" : newSummary);
        return hasPolicy || hasImageSummary;
    }

    private static List<String> parseImageUrls(String csv) {
        if (csv == null || csv.isEmpty()) return java.util.Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) {
            String u = s.trim();
            if (!u.isEmpty()) out.add(u);
            if (out.size() >= MAX_IMAGES_PER_POST) break;
        }
        return out;
    }

    private static String buildUserText(WebSearchNews n) {
        StringBuilder sb = new StringBuilder();
        sb.append("品牌：").append(safe(n.getBrandName())).append("\n");
        if (n.getEventSummary() != null && !n.getEventSummary().isEmpty()) {
            sb.append("已分类摘要：").append(n.getEventSummary()).append("\n");
        }
        String content = n.getContent();
        if (content != null && !content.isEmpty()) {
            sb.append("原帖正文：")
              .append(content.length() > 300 ? content.substring(0, 300) + "…" : content)
              .append("\n");
        }
        sb.append("\n请按 system 指令分析上述图片，返回 JSON。");
        return sb.toString();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static class Result {
        public final int processed;
        public final int policyHit;
        public final int failed;
        public Result(int processed, int policyHit, int failed) {
            this.processed = processed;
            this.policyHit = policyHit;
            this.failed = failed;
        }
    }
}
