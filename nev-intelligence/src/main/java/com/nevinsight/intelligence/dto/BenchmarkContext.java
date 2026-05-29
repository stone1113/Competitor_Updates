package com.nevinsight.intelligence.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 深度对标统一数据载体（DataCollector 输出 → 5 Analyst 输入）。
 * 含本品 + N 个竞品的所有维度数据。
 */
@Data
@NoArgsConstructor
public class BenchmarkContext {

    /** 本品车型名（如「猛士M817」）*/
    private String selfModel;

    /** 本品级别（如「D级越野SUV」），从 autohome_spec 的「级别」参数推断 */
    private String selfClass;

    /** 顺序：self 在前，竞品按用户选择顺序 */
    private List<String> allModels = new ArrayList<>();

    /** modelName → 该车型的完整数据 */
    private Map<String, ModelData> modelDataMap = new LinkedHashMap<>();

    /** DataCollector 完成时间戳 */
    private Long collectedTs;

    @Data
    @NoArgsConstructor
    public static class ModelData {
        /** 车型名 */
        private String modelName;
        /** 品牌 */
        private String brandName;
        /** 角色 self | competitor */
        private String role;
        /** 汽车之家 series_id */
        private String autohomeSeriesId;
        /** 盖世汽车 series_id（可为空）*/
        private String gasgooSeriesId;

        /** 关键参数 paramName → paramValue（规范化后，已过滤空值）*/
        private Map<String, String> specs = new LinkedHashMap<>();

        /** 近 3 月销量 [{year, month, salesCount, ytdCount}] */
        private List<SalesSnapshot> recentSales = new ArrayList<>();

        /** 36h 内官号价格金融事件文本列表 */
        private List<String> priceEvents = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class SalesSnapshot {
        private Integer year;
        private Integer month;
        private Integer salesCount;
        private Integer ytdCount;
    }
}
