package com.nevinsight.intelligence.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单维度分析师输出 schema。5 个 Analyst Agent 共用，仅 dimension 不同。
 * 严格 JSON：dimension / winner / scores / keyFindings
 */
@Data
@NoArgsConstructor
public class AnalystResult {

    /** 维度名（动力/车身/越野通过性/价格价值/销量市场）*/
    private String dimension;

    /** 该维度胜出车型名 */
    private String winner;

    /** modelName → 0-10 评分（含本品 + 所有竞品） */
    private Map<String, Integer> scores = new LinkedHashMap<>();

    /** 3-5 句关键发现，每句含具体数字 */
    private List<String> keyFindings = new ArrayList<>();

    /** Agent 调用耗时（毫秒），用于性能观测 */
    private Long elapsedMs;

    /** 失败时填充错误信息，scores/findings 可能为空 */
    private String error;
}
