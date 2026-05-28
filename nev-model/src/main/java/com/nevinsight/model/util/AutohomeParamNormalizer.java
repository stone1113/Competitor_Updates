package com.nevinsight.model.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 汽车之家参数名规范化：autohome 用 font-face 反爬把关键字段名替换为 `<span class='hs_kw…'></span>`，
 * 我们的 scraper 用 `·` 占位。本类按观察到的 pattern 映射回真名（按 category 上下文消歧）。
 *
 * Pattern 数据来源：实际抓的 7 个对标车系 + 1 个本品的 distinct param_name 列表（2026-05 观察）。
 * 维护策略：autohome 字体每月可能变，新出现的 `·` 占位模式补到本表即可，无需重抓数据。
 */
public final class AutohomeParamNormalizer {

    private AutohomeParamNormalizer() {}

    /** 全局精确匹配（category 无关）。 */
    private static final Map<String, String> EXACT = new LinkedHashMap<>();

    /** 仅在指定 category 下生效的覆盖（同 paramName 在不同 category 含义不同时用）。 */
    private static final Map<String, Map<String, String>> CATEGORY_OVERRIDE = new LinkedHashMap<>();

    static {
        // ===== 基本参数 =====
        EXACT.put("厂·", "厂商");
        EXACT.put("厂····(·)", "厂商指导价(万)");
        EXACT.put("上市·", "上市时间");
        EXACT.put("车型·", "车型名称");
        EXACT.put("整车·", "整车质保");
        EXACT.put("首任车主·政策", "首任车主质保政策");
        EXACT.put("··(kg)", "整备质量(kg)");
        EXACT.put("最大满载·(kg)", "最大满载质量(kg)");
        EXACT.put("最大载重·(kg)", "最大载重质量(kg)");
        EXACT.put("准拖挂车总·(kg)", "准拖挂车总质量(kg)");
        EXACT.put("NEDC··(L/100km)", "NEDC综合油耗(L/100km)");
        EXACT.put("WLTC··(L/100km)", "WLTC综合油耗(L/100km)");
        EXACT.put("最低荷电状态·(L/100km)", "最低荷电状态油耗(L/100km)");
        EXACT.put("最低荷电状态·(L/100km)WLTC", "最低荷电状态油耗WLTC(L/100km)");
        EXACT.put("油电·燃料消耗量(L/100km)", "油电综合燃料消耗量(L/100km)");

        // ===== 发动机 =====
        EXACT.put("发动机型·", "发动机型号");
        EXACT.put("·材料", "缸体材料");
        EXACT.put("·方式", "供油方式");
        EXACT.put("·标·", "环保标准");
        EXACT.put("·数(个)", "缸数(个)");
        EXACT.put("··形式", "气缸排列形式");
        EXACT.put("每缸·(个)", "每缸气门数(个)");
        EXACT.put("·(L)", "排量(L)");
        EXACT.put("·(mL)", "排量(mL)");
        // ·(mm) 跨 category 歧义：发动机 → 缸径；车身 → 高度
        addCatOverride("发动机", "·(mm)", "缸径(mm)");
        addCatOverride("车身", "·(mm)", "高度(mm)");

        // ===== 变速箱 ===== （这里参数无 · 占位）

        // ===== 电动机 =====
        EXACT.put("·电动机最大功率(kW)", "前电动机最大功率(kW)");
        EXACT.put("·电动机最大扭矩(N·m)", "前电动机最大扭矩(N·m)");
        EXACT.put("·电动机品牌", "前电动机品牌");
        EXACT.put("·电动机型·", "前电动机型号");
        EXACT.put("后电动机型·", "后电动机型号");
        EXACT.put("三电系统·", "三电系统质保");
        EXACT.put("三电首任车主·政策", "三电首任车主质保政策");
        EXACT.put("系统·功率(kW)", "系统综合功率(kW)");
        EXACT.put("系统·扭矩(N·m)", "系统综合扭矩(N·m)");
        EXACT.put("系统·马力(Ps)", "系统综合马力(Ps)");

        // ===== 电池/充电 =====
        EXACT.put("·功能", "快充功能");
        EXACT.put("··位置", "充电口位置");
        EXACT.put("CLTC·续航(km)", "CLTC纯电续航(km)");
        EXACT.put("WLTC·续航(km)", "WLTC纯电续航(km)");
        EXACT.put("对外·放电功率(kW)", "对外放电功率(kW)");
        EXACT.put("电池··(小时)", "电池充电时间(小时)");
        EXACT.put("电池··范围(%)", "电池荷电状态范围(%)");
        EXACT.put("电池组·", "电池组质保");
        EXACT.put("百公里耗·(kWh/100km)", "百公里耗电量(kWh/100km)");

        // ===== 底盘转向 =====
        EXACT.put("前·类型", "前悬挂类型");
        EXACT.put("··结构", "悬挂结构");
        EXACT.put("··类型", "后悬挂类型");

        // ===== 车身 =====
        EXACT.put("·阻系数(Cd)", "风阻系数(Cd)");
        EXACT.put("后备厢·(L)", "后备厢容积(L)");
        EXACT.put("·(个)", "车门数(个)");
        EXACT.put("··(L)", "油箱容积(L)");
        EXACT.put("·轮距(mm)", "前轮距(mm)");
        EXACT.put("满载最小·(mm)", "满载最小离地间隙(mm)");
        EXACT.put("最小·(mm)", "最小离地间隙(mm)");

        // ===== 车轮制动 =====
        EXACT.put("··", "主刹车类型");
        EXACT.put("·制动类型", "后制动类型");
        EXACT.put("·类型", "前制动类型");
        EXACT.put("·制动器类型", "驻车制动器类型");
        EXACT.put("·放置方式", "备胎放置方式");
        EXACT.put("·轮胎·", "备用轮胎规格");
    }

    private static void addCatOverride(String category, String paramName, String mapped) {
        CATEGORY_OVERRIDE.computeIfAbsent(category, k -> new LinkedHashMap<>())
                          .put(paramName, mapped);
    }

    /**
     * 按 category + paramName 还原真名。无映射时原样返回。
     */
    public static String normalize(String category, String paramName) {
        if (paramName == null || paramName.isEmpty()) return paramName;
        // 先按 category override
        if (category != null) {
            Map<String, String> sub = CATEGORY_OVERRIDE.get(category);
            if (sub != null) {
                String v = sub.get(paramName);
                if (v != null) return v;
            }
        }
        // 再走全局 exact map
        return EXACT.getOrDefault(paramName, paramName);
    }

    /**
     * 检测「值是否为空/无意义」（用于过滤全空行）。
     * 空字符串、null、'-'、'·'(单字符占位) 都视为空。
     */
    public static boolean isBlankValue(String value) {
        if (value == null) return true;
        String t = value.trim();
        if (t.isEmpty()) return true;
        if ("-".equals(t) || "·".equals(t)) return true;
        return false;
    }
}
