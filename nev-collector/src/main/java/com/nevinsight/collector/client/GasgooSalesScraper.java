package com.nevinsight.collector.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 盖世汽车车型销量页爬虫（纯静态 HTML 表格）。
 *
 * URL: https://m.gasgoo.com/qcxl/cxxl/{year}/{month}/{seriesId}
 *
 * 页面表格结构（一定 4 列 = 当月/上月/上上月/本年累计；行数变动 4-5 行）：
 *   header   2026-04 | 2026-03 | 2026-02 | 2026累计
 *   row1     仰望U8       184      158      156       815
 *   row2     比亚迪汽车   314100   295693   187782    1003093
 *   row3     D级          48278    36171    23008     140929
 *   row4     SUV (车身)  1279961  1428865  915870    4767432  ← 可选
 *
 * 第一列是行标签（车型/厂商/级别/车身），后 4 列是月度数字。
 * 我们只关心查询 month 对应的列值（一般是第 1 列即当月）。
 */
@Slf4j
@Component
public class GasgooSalesScraper {

    private static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final Pattern TD_PATTERN = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern TAG_STRIP = Pattern.compile("<[^>]+>");
    private static final Pattern PERIOD_HEADER = Pattern.compile("^(\\d{4})-(\\d{1,2})$");

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 抓单个车型某年某月的销量。
     * @return ScrapeResult；若页面无目标月份列或抓取失败返回 null
     */
    public ScrapeResult scrape(String gasgooSeriesId, int year, int month) {
        String url = String.format("https://m.gasgoo.com/qcxl/cxxl/%d/%d/%s", year, month, gasgooSeriesId);
        String html;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", UA);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            html = resp.getBody();
            if (html == null || html.length() < 500) {
                log.warn("[Gasgoo] page too small for {}: {} bytes", url, html == null ? 0 : html.length());
                return null;
            }
        } catch (Exception e) {
            log.warn("[Gasgoo] fetch failed url={} err={}", url, e.getMessage());
            return null;
        }

        List<String> tds = extractTdTexts(html);
        if (tds.size() < 9) {
            log.warn("[Gasgoo] td count too low for {}: {}", url, tds.size());
            return null;
        }

        // 找列号：扫前 N 个 td，找到 4 个 yyyy-mm 形式 + 1 个 累计；目标月对应索引就是当月列
        // 也允许目标月不在第 1 列（如果用户请求历史月份）
        int colIndex = locateMonthColumn(tds, year, month);
        if (colIndex < 0) {
            log.warn("[Gasgoo] period {}-{} not found in page; tds[0..6]={}", year, month, tds.subList(0, Math.min(6, tds.size())));
            return null;
        }
        int ytdCol = locateYtdColumn(tds, year);

        // header 后第 1 行就是车型行，第 2 行是厂商，第 3 行是级别
        // header 列数 4 → 行起始 td index = 4，每行 5 td（label + 4 numbers）
        ScrapeResult res = new ScrapeResult();
        res.sourceUrl = url;
        res.year = year;
        res.month = month;

        // 找到 header 结束位置（最后一个匹配 yyyy-mm 或累计 的 td 后面的下一个 td）
        int headerEnd = findHeaderEnd(tds);
        int rowOffset = headerEnd; // 第 1 行 label 的 td 索引

        // row1: 车型
        if (rowOffset + 4 < tds.size()) {
            res.modelLabel = tds.get(rowOffset);
            res.salesCount = parseInt(tds.get(rowOffset + 1 + colIndex));
            if (ytdCol >= 0) res.ytdCount = parseInt(tds.get(rowOffset + 1 + ytdCol));
        }
        // row2: 厂商
        if (rowOffset + 9 < tds.size()) {
            res.brandLabel = tds.get(rowOffset + 5);
            res.brandTotal = parseInt(tds.get(rowOffset + 6 + colIndex));
        }
        // row3: 级别
        if (rowOffset + 14 < tds.size()) {
            res.segmentLabel = tds.get(rowOffset + 10);
            res.segmentTotal = parseInt(tds.get(rowOffset + 11 + colIndex));
        }
        return res;
    }

    /** 抽 td 纯文本（去标签 + trim）。 */
    private static List<String> extractTdTexts(String html) {
        Matcher m = TD_PATTERN.matcher(html);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            String raw = m.group(1);
            String text = TAG_STRIP.matcher(raw).replaceAll("").replace("&nbsp;", " ").trim();
            out.add(text);
        }
        return out;
    }

    /** 在 header 区找到 yyyy-mm 列对应的列号（0-based, 0=最新月）。 */
    private static int locateMonthColumn(List<String> tds, int year, int month) {
        int col = 0;
        for (String t : tds) {
            Matcher m = PERIOD_HEADER.matcher(t);
            if (m.matches()) {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                if (y == year && mo == month) return col;
                col++;
                if (col >= 4) break;
            } else if (t.contains("累计")) {
                break;
            }
        }
        return -1;
    }

    /** 「YYYY累计」所在列（相对 header 起始）。 */
    private static int locateYtdColumn(List<String> tds, int year) {
        int col = 0;
        for (String t : tds) {
            Matcher m = PERIOD_HEADER.matcher(t);
            if (m.matches()) {
                col++;
            } else if (t.contains("累计")) {
                return col;
            } else if (col > 0) {
                break; // header 已结束
            }
        }
        return -1;
    }

    /** 找 header 结束后第一个数据行 td 的索引（label）。
     *  注意：盖世表格左上角是空 td，要跳过；header 主体是 4 个 yyyy-MM / 累计 单元。 */
    private static int findHeaderEnd(List<String> tds) {
        int i = 0;
        while (i < tds.size()) {
            String t = tds.get(i);
            if (t.isEmpty() || PERIOD_HEADER.matcher(t).matches() || t.contains("累计")) {
                i++;
                continue;
            }
            return i;
        }
        return tds.size();
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty() || "-".equals(s)) return null;
        try { return Integer.parseInt(s.replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    public static class ScrapeResult {
        public String sourceUrl;
        public int year;
        public int month;
        public String modelLabel;
        public Integer salesCount;
        public Integer brandTotal;
        public String brandLabel;
        public Integer segmentTotal;
        public String segmentLabel;
        public Integer ytdCount;

        public boolean isValid() { return salesCount != null; }
    }
}
