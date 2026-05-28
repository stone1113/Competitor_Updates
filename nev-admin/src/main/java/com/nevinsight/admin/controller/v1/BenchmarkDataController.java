package com.nevinsight.admin.controller.v1;

import com.nevinsight.common.ApiResponse;
import com.nevinsight.intelligence.dto.BenchmarkContext;
import com.nevinsight.intelligence.dto.BenchmarkContext.ModelData;
import com.nevinsight.intelligence.dto.BenchmarkContext.SalesSnapshot;
import com.nevinsight.intelligence.service.DeepBenchmarkDataCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * v9.6 给 RAGFlow Agent 的 Invoke 节点用的数据 API。
 *
 * 端点设计：分维度返回精简数据，避免单个 prompt 过大。
 *   GET /api/v1/benchmark/data?selfModel=...&competitorModels=...&dimension=power|body|offroad|price|sales|all
 *
 * 返回结构稳定 — RAGFlow Agent 配置一次后不易破坏。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/benchmark")
@RequiredArgsConstructor
public class BenchmarkDataController {

    /** 维度参数关键词白名单（同 DeepBenchmarkAgent.Dimension） */
    private static final Map<String, List<String>> DIM_PARAMS = new LinkedHashMap<>();
    static {
        DIM_PARAMS.put("power", List.of(
                "最大功率", "最大扭矩", "最大马力", "电动机总功率", "电动机总扭矩", "电动机总马力",
                "后电动机最大功率", "前电动机最大功率",
                "官方0-100km/h加速", "官方0-50km/h加速",
                "WLTC综合油耗", "NEDC综合油耗", "百公里耗电量",
                "NEDC纯电续航里程", "CLTC纯电续航", "WLTC纯电续航",
                "电池能量", "电池能量密度", "电池类型", "快充功能",
                "电池充电时间", "电池荷电状态范围", "对外放电功率",
                "能源类型", "驱动方式", "驱动电机数"));
        DIM_PARAMS.put("body", List.of(
                "长*宽*高", "高度", "前轮距", "车身结构", "车门开启方式",
                "整备质量", "最大满载质量", "最大载重质量", "准拖挂车总质量",
                "后备厢容积", "油箱容积", "车门数", "座位数",
                "最高车速", "上市时间", "厂商", "厂商指导价", "级别"));
        DIM_PARAMS.put("offroad", List.of(
                "接近角", "离去角", "纵向通过角", "最大涉水深度",
                "最大爬坡度", "最大爬坡角度", "最小离地间隙", "满载最小离地间隙",
                "最小转弯半径", "前悬挂类型", "后悬挂类型", "车体结构",
                "悬挂结构", "前制动类型", "后制动类型", "驻车制动器类型"));
        DIM_PARAMS.put("price", List.of(
                "厂商指导价", "上市时间", "厂商", "首任车主质保政策",
                "整车质保", "电池组质保", "三电系统质保", "三电首任车主质保政策"));
    }

    private final DeepBenchmarkDataCollector dataCollector;

    /**
     * 拉取本品 + 竞品的某个维度数据（给 RAGFlow Invoke 节点用）。
     *
     * 示例：
     *   GET /api/v1/benchmark/data?selfModel=猛士M817&competitorModels=方程豹豹5,问界M8 REEV&dimension=power
     *   → {"selfModel":"猛士M817","competitorModels":[...],"dimension":"power","table":"| 参数 | ... |","sales":[...],"priceEvents":[...]}
     */
    @GetMapping("/data")
    public ApiResponse<Map<String, Object>> data(
            @RequestParam String selfModel,
            @RequestParam String competitorModels,
            @RequestParam(defaultValue = "all") String dimension) {
        List<String> competitors = new ArrayList<>();
        for (String s : competitorModels.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) competitors.add(t);
        }
        if (competitors.isEmpty()) {
            return ApiResponse.error(400, "至少选 1 个竞品");
        }
        BenchmarkContext ctx;
        try {
            ctx = dataCollector.collect(selfModel, competitors);
        } catch (Exception e) {
            log.error("[BenchmarkData] collect 失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "数据采集失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("selfModel", selfModel);
        data.put("selfClass", ctx.getSelfClass());
        data.put("competitorModels", competitors);
        data.put("dimension", dimension);
        data.put("models", ctx.getAllModels());

        // 按维度组装表格（markdown）+ 销量 / 价格事件
        if ("sales".equals(dimension)) {
            data.put("salesTable", buildSalesTable(ctx));
        } else if ("price".equals(dimension)) {
            data.put("paramTable", buildParamTable(ctx, DIM_PARAMS.get("price")));
            data.put("priceEvents", buildPriceEvents(ctx));
        } else if (DIM_PARAMS.containsKey(dimension)) {
            data.put("paramTable", buildParamTable(ctx, DIM_PARAMS.get(dimension)));
        } else {
            // all：返回全部维度数据，每维度一段
            Map<String, Object> all = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : DIM_PARAMS.entrySet()) {
                all.put(e.getKey(), buildParamTable(ctx, e.getValue()));
            }
            all.put("sales", buildSalesTable(ctx));
            all.put("priceEvents", buildPriceEvents(ctx));
            data.put("dimensions", all);
        }
        return ApiResponse.success(data);
    }

    private String buildParamTable(BenchmarkContext ctx, List<String> paramKeys) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 参数 |");
        for (String m : ctx.getAllModels()) sb.append(" ").append(m).append(" |");
        sb.append("\n|---|");
        for (int i = 0; i < ctx.getAllModels().size(); i++) sb.append("---|");
        sb.append("\n");
        for (String paramKey : paramKeys) {
            boolean anyValue = ctx.getModelDataMap().values().stream()
                    .anyMatch(md -> findParam(md.getSpecs(), paramKey) != null);
            if (!anyValue) continue;
            sb.append("| ").append(paramKey).append(" |");
            for (String m : ctx.getAllModels()) {
                ModelData md = ctx.getModelDataMap().get(m);
                String v = md == null ? null : findParam(md.getSpecs(), paramKey);
                sb.append(" ").append(v == null ? "—" : v).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String findParam(Map<String, String> specs, String key) {
        if (specs.containsKey(key)) return specs.get(key);
        for (Map.Entry<String, String> e : specs.entrySet()) {
            if (e.getKey() != null && e.getKey().contains(key)) return e.getValue();
        }
        return null;
    }

    private List<Map<String, Object>> buildSalesTable(BenchmarkContext ctx) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String m : ctx.getAllModels()) {
            ModelData md = ctx.getModelDataMap().get(m);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("model", m);
            if (md == null || md.getRecentSales().isEmpty()) {
                row.put("monthly", Collections.emptyList());
                row.put("ytd", null);
            } else {
                row.put("monthly", md.getRecentSales().stream().map(s -> Map.of(
                        "year", s.getYear(), "month", s.getMonth(), "count", s.getSalesCount()
                )).collect(Collectors.toList()));
                row.put("ytd", md.getRecentSales().get(0).getYtdCount());
            }
            out.add(row);
        }
        return out;
    }

    private Map<String, List<String>> buildPriceEvents(BenchmarkContext ctx) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String m : ctx.getAllModels()) {
            ModelData md = ctx.getModelDataMap().get(m);
            out.put(m, md == null ? Collections.emptyList() : md.getPriceEvents());
        }
        return out;
    }
}
