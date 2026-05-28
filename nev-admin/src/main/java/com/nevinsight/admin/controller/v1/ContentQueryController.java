package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nevinsight.collector.service.PlatformQueryService;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.dto.response.BuzzItemResponse;
import com.nevinsight.model.dto.response.CommentItemResponse;
import com.nevinsight.model.dto.response.ContentPageResponse;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
public class ContentQueryController {

    private final WebSearchNewsMapper webSearchNewsMapper;
    private final PlatformQueryService platformQueryService;

    /**
     * 查询采集到的搜索引擎新闻
     */
    @GetMapping("/news")
    public ApiResponse<ContentPageResponse<WebSearchNews>> queryNews(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlTo,
            @RequestParam(required = false) String publishedFrom,
            @RequestParam(required = false) String publishedTo,
            @RequestParam(required = false) String sourceTool,
            @RequestParam(defaultValue = "crawlDate") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        QueryWrapper<WebSearchNews> qw = new QueryWrapper<>();
        if (brand != null && !brand.isEmpty()) qw.eq("brand_name", brand);
        if (sourceTool != null && !sourceTool.isEmpty()) qw.eq("source_tool", sourceTool);
        if (crawlFrom != null) qw.ge("crawl_date", crawlFrom);
        if (crawlTo != null) qw.le("crawl_date", crawlTo);
        if (publishedFrom != null && !publishedFrom.isEmpty()) qw.ge("published_date", publishedFrom);
        if (publishedTo != null && !publishedTo.isEmpty()) qw.le("published_date", publishedTo);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("title", kw).or().like("content", kw).or().like("search_query", kw));
        }
        if ("relevance".equalsIgnoreCase(sortBy)) {
            qw.orderByDesc("relevance_score");
        } else {
            qw.orderByDesc("crawl_date").orderByDesc("id");
        }

        Page<WebSearchNews> p = new Page<>(page, pageSize);
        Page<WebSearchNews> result = webSearchNewsMapper.selectPage(p, qw);

        ContentPageResponse<WebSearchNews> resp = ContentPageResponse.<WebSearchNews>builder()
                .list(result.getRecords())
                .total(result.getTotal())
                .page(page)
                .pageSize(pageSize)
                .build();
        return ApiResponse.success(resp);
    }

    /**
     * 查询采集到的社交平台内容（5 张表 UNION ALL）
     */
    @GetMapping("/social")
    public ApiResponse<ContentPageResponse<BuzzItemResponse>> querySocial(
            @RequestParam String brand,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate crawlTo,
            @RequestParam(required = false) String platforms,
            @RequestParam(defaultValue = "engagement") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        List<String> platformList = (platforms == null || platforms.isEmpty())
                ? Collections.emptyList()
                : Arrays.asList(platforms.split(","));

        ContentPageResponse<BuzzItemResponse> resp = platformQueryService.queryBuzzWithFilter(
                brand, crawlFrom, crawlTo, platformList, keyword, sortBy, page, pageSize);
        return ApiResponse.success(resp);
    }

    /** 查询某条社交平台内容下的评论，按点赞降序 */
    @GetMapping("/comments")
    public ApiResponse<List<CommentItemResponse>> queryComments(
            @RequestParam String platform,
            @RequestParam String contentId,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(platformQueryService.queryComments(platform, contentId, limit));
    }
}
