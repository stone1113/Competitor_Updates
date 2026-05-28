package com.nevinsight.collector.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Fetches and extracts readable article text for Bocha-discovered web pages.
 * Search snippets are useful for discovery, but market analysis needs the page body.
 */
@Slf4j
@Service
public class WebArticleContentService {

    private static final int MIN_ARTICLE_LENGTH = 260;
    private static final int MAX_ARTICLE_LENGTH = 12000;
    private static final int MAX_BODY_SIZE = 3 * 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final List<String> ARTICLE_SELECTORS = Arrays.asList(
            "article", "main", ".article", ".article-content", ".article_content",
            ".article-text", ".articleText", ".post-content", ".post_text",
            ".content", ".news-content", ".news_text", ".text", "#article",
            "#article_content", "#content", ".main-content", ".detail-content"
    );

    public Optional<String> fetchArticleText(String url, String fallbackSnippet) {
        if (url == null || url.trim().isEmpty()) return Optional.empty();
        String normalizedUrl = url.trim();
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            return Optional.empty();
        }
        try {
            Connection.Response response = Jsoup.connect(normalizedUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.baidu.com/")
                    .timeout(10000)
                    .maxBodySize(MAX_BODY_SIZE)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .followRedirects(true)
                    .execute();
            if (response.statusCode() >= 400) {
                log.debug("[ArticleFetch] skip url={} status={}", normalizedUrl, response.statusCode());
                return Optional.empty();
            }
            String contentType = response.contentType();
            if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("html")
                    && !contentType.toLowerCase(Locale.ROOT).contains("text")) {
                return Optional.empty();
            }
            Document doc = response.parse();
            removeNoiseNodes(doc);
            String article = normalizeArticleText(extractBestCandidate(doc));
            if (!isUsefulArticle(article, fallbackSnippet)) {
                return Optional.empty();
            }
            return Optional.of(truncate(article, MAX_ARTICLE_LENGTH));
        } catch (Exception e) {
            log.debug("[ArticleFetch] failed url={} err={}", normalizedUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private static void removeNoiseNodes(Document doc) {
        if (doc == null) return;
        doc.select("script,style,noscript,iframe,svg,canvas,form,input,button,nav,footer,header,aside,"
                + ".nav,.navbar,.footer,.header,.comment,.comments,.share,.related,.recommend,"
                + ".advert,.ad,.ads,.breadcrumb,.crumb,.pagination").remove();
    }

    private static String extractBestCandidate(Document doc) {
        if (doc == null) return "";
        Candidate best = new Candidate("", 0);
        for (String selector : ARTICLE_SELECTORS) {
            Elements elements = doc.select(selector);
            for (Element element : elements) {
                Candidate candidate = scoreElement(element);
                if (candidate.score > best.score) best = candidate;
            }
        }
        Candidate body = scoreElement(doc.body());
        if (body.score > best.score) best = body;
        return best.text;
    }

    private static Candidate scoreElement(Element element) {
        if (element == null) return new Candidate("", 0);
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        Elements paragraphs = element.select("p, h1, h2, h3, li");
        for (Element p : paragraphs) {
            String line = normalizeLine(p.text());
            if (isArticleLine(line)) lines.add(line);
        }
        if (lines.isEmpty()) {
            String text = normalizeLine(element.text());
            if (isArticleLine(text)) lines.add(text);
        }
        String text = String.join("\n", lines);
        int punctuation = countMatches(text, "，。；：、,.!?！？");
        int score = text.length() + lines.size() * 80 + punctuation * 5;
        return new Candidate(text, score);
    }

    private static String normalizeArticleText(String text) {
        if (text == null || text.isEmpty()) return "";
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (String raw : text.split("[\\r\\n]+")) {
            String line = normalizeLine(raw);
            if (isArticleLine(line)) lines.add(line);
        }
        return String.join("\n", lines).trim();
    }

    private static String normalizeLine(String text) {
        if (text == null) return "";
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("^[　\\s]+|[　\\s]+$", "")
                .trim();
    }

    private static boolean isArticleLine(String line) {
        if (line == null) return false;
        String v = line.trim();
        if (v.length() < 8) return false;
        if (v.matches(".*(责任编辑|责编|编辑：|声明：|免责声明|版权归|版权所有|未经授权|扫码|二维码|下载APP|打开APP|分享至|微信|微博|收藏|点赞|评论|更多精彩).*")) {
            return false;
        }
        if (v.matches("^[\\d\\s:：\\-—–/年月日]+$")) return false;
        return true;
    }

    private static boolean isUsefulArticle(String article, String fallbackSnippet) {
        if (article == null || article.length() < MIN_ARTICLE_LENGTH) return false;
        int lineCount = article.split("\\n+").length;
        int punctuation = countMatches(article, "，。；：、,.!?！？");
        if (lineCount < 3 && punctuation < 8) return false;
        String snippet = fallbackSnippet == null ? "" : fallbackSnippet.trim();
        if (snippet.length() >= article.length() && normalizeComparable(snippet).contains(normalizeComparable(article))) {
            return false;
        }
        return true;
    }

    private static String normalizeComparable(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\s\\p{Punct}，。；：、！？（）【】《》“”‘’·|/\\-—_]+", "").trim();
    }

    private static int countMatches(String text, String chars) {
        if (text == null || text.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (chars.indexOf(text.charAt(i)) >= 0) count++;
        }
        return count;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    private static class Candidate {
        private final String text;
        private final int score;

        private Candidate(String text, int score) {
            this.text = text == null ? "" : text;
            this.score = score;
        }
    }
}
