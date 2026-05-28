package com.nevinsight.report.feishu;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书 interactive 卡片构建共用工具：节点构造 + 文本处理 + 徽章等纯静态函数。
 *
 * 不持有状态。所有方法 static。被 FeishuCardBuilder（舆情日报）和
 * CompetitorCardBuilder（竞品分析日报）共用。
 */
public final class FeishuCardUtils {

    public static final int MAX_TITLE = 50;
    public static final int MAX_SUMMARY = 70;
    public static final int MAX_SECTION_BYTES = 2500;

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#[^#]+#");
    private static final Pattern ENGAGEMENT_PATTERN = Pattern.compile("互动\\s*(\\d+)");

    private FeishuCardUtils() {}

    // ====== 节点构造 ======

    public static Map<String, Object> textNode(String tag, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag", tag);
        m.put("content", content);
        return m;
    }

    public static Map<String, Object> hr() {
        return Collections.singletonMap("tag", "hr");
    }

    public static Map<String, Object> divNode(String tag, String content) {
        Map<String, Object> div = new LinkedHashMap<>();
        div.put("tag", "div");
        div.put("text", textNode(tag, content));
        return div;
    }

    /** 在 elements 末尾追加：hr + lark_md div。内容为空则跳过。 */
    public static void appendSection(List<Object> elements, String content) {
        if (content == null || content.isEmpty()) return;
        elements.add(hr());
        elements.add(divNode("lark_md", content));
    }

    /** 跳转按钮（v2：用 behaviors[open_url] 替代 multi_url） */
    public static Map<String, Object> actionButton(String text, String url) {
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("tag", "button");
        btn.put("type", "primary");
        btn.put("text", textNode("plain_text", text));
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("type", "open_url");
        behavior.put("default_url", url);
        behavior.put("pc_url", url);
        behavior.put("ios_url", url);
        behavior.put("android_url", url);
        btn.put("behaviors", List.of(behavior));
        return btn;
    }

    /** 底部细体灰字注脚 */
    public static Map<String, Object> noteFooter() {
        String content = "<font color='grey'><i>NEV-Insight · "
                + new SimpleDateFormat("MM-dd HH:mm").format(new Date()) + "</i></font>";
        return divNode("lark_md", content);
    }

    // ====== 文本处理 ======

    public static String safe(String s) {
        return s == null ? "" : s;
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    public static String stripHashtags(String s) {
        if (s == null) return "";
        return HASHTAG_PATTERN.matcher(s).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    // ====== 情感徽章 / 状态色 ======

    public static String sentimentBadge(String tag) {
        if (tag == null) return "<font color='grey'>·</font>";
        if (tag.contains("非常正面")) return "<font color='green'>**强正**</font>";
        if (tag.contains("非常负面")) return "<font color='red'>**强负**</font>";
        if (tag.contains("正面") || tag.contains("正向") || tag.contains("积极")) return "<font color='green'>正</font>";
        if (tag.contains("负面") || tag.contains("负向") || tag.contains("消极")) return "<font color='red'>负</font>";
        return "<font color='grey'>中</font>";
    }

    public static String templateColor(String level) {
        if ("red".equalsIgnoreCase(level)) return "red";
        if ("yellow".equalsIgnoreCase(level)) return "orange";
        return "green";
    }

    public static String statusLabel(String level) {
        if ("red".equalsIgnoreCase(level)) return "🔴 预警";
        if ("yellow".equalsIgnoreCase(level)) return "🟡 关注";
        return "🟢 平稳";
    }

    public static String statusColor(String level) {
        if ("red".equalsIgnoreCase(level)) return "red";
        if ("yellow".equalsIgnoreCase(level)) return "orange";
        return "green";
    }

    // ====== 互动量提取 ======

    public static String compactEngagement(String stats) {
        if (stats == null) return "";
        Matcher m = ENGAGEMENT_PATTERN.matcher(stats);
        if (m.find()) {
            try {
                long v = Long.parseLong(m.group(1));
                return "互动 " + humanNumber(v);
            } catch (NumberFormatException ignored) {}
        }
        return "";
    }

    public static String humanNumber(long n) {
        if (n >= 100_000_000L) return String.format("%.1f亿", n / 100_000_000.0);
        if (n >= 10_000L) return String.format("%.1f万", n / 10_000.0);
        return String.valueOf(n);
    }
}
