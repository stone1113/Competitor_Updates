package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.intelligence.agent.EventExtractionAgent;
import com.nevinsight.intelligence.agent.EventExtractionAgent.EventResult;
import com.nevinsight.intelligence.agent.EventExtractionAgent.NewsLite;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 事件浏览 + 手动修分类 API。
 *
 * 设计：运营在前端看 web_search_news 列表，发现 LLM 分错时点 cell 改 event_type，
 * 立即生效，下一次日报就用新分类。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventBrowserController {

    private final WebSearchNewsMapper newsMapper;
    private final EventExtractionAgent agent;

    /** 列事件：可按 since(ms) / category / event_type / brand_name 过滤，分页。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long since,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {

        if (since == null) {
            since = System.currentTimeMillis() - 36L * 3600 * 1000;
        }

        LambdaQueryWrapper<WebSearchNews> qw = new LambdaQueryWrapper<>();
        qw.ge(WebSearchNews::getAddTs, since);
        if (category != null && !category.isEmpty()) qw.eq(WebSearchNews::getCategory, category);
        if (eventType != null && !eventType.isEmpty()) {
            if ("pending".equals(eventType)) qw.isNull(WebSearchNews::getEventType);
            else qw.eq(WebSearchNews::getEventType, eventType);
        }
        if (brand != null && !brand.isEmpty()) qw.eq(WebSearchNews::getBrandName, brand);
        qw.orderByDesc(WebSearchNews::getEventImportance).orderByDesc(WebSearchNews::getAddTs);

        Page<WebSearchNews> p = new Page<>(page, pageSize);
        IPage<WebSearchNews> result = newsMapper.selectPage(p, qw);

        Map<String, Object> out = new HashMap<>();
        out.put("records", result.getRecords());
        out.put("total", result.getTotal());
        out.put("page", page);
        out.put("pageSize", pageSize);
        return ApiResponse.success(out);
    }

    /** 36h 内按 event_type 分布统计（前端 tab 计数用）。 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats(
            @RequestParam(required = false) Long since) {
        if (since == null) since = System.currentTimeMillis() - 36L * 3600 * 1000;
        // 用 MyBatis Plus 的 group 查询
        String[] types = {"launch", "price_finance", "campaign", "sales_milestone", "other", "spam"};
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String t : types) {
            counts.put(t, newsMapper.selectCount(
                    new LambdaQueryWrapper<WebSearchNews>()
                            .ge(WebSearchNews::getAddTs, since)
                            .eq(WebSearchNews::getEventType, t)));
        }
        counts.put("pending", newsMapper.selectCount(
                new LambdaQueryWrapper<WebSearchNews>()
                        .ge(WebSearchNews::getAddTs, since)
                        .isNull(WebSearchNews::getEventType)));
        counts.put("total", newsMapper.selectCount(
                new LambdaQueryWrapper<WebSearchNews>()
                        .ge(WebSearchNews::getAddTs, since)));
        return ApiResponse.success(counts);
    }

    /** 修单条 event_type / importance / summary。 */
    @PutMapping("/{id}")
    public ApiResponse<WebSearchNews> update(@PathVariable Long id, @RequestBody UpdateReq req) {
        WebSearchNews existing = newsMapper.selectById(id);
        if (existing == null) return ApiResponse.error(404, "not found");
        if (req.eventType != null) {
            if (!EventExtractionAgent.ALLOWED_EVENT_TYPES.contains(req.eventType)) {
                return ApiResponse.error(400, "invalid event_type");
            }
            existing.setEventType(req.eventType);
        }
        if (req.importance != null) {
            int imp = Math.max(0, Math.min(10, req.importance));
            existing.setEventImportance(imp);
        }
        if (req.eventSummary != null) existing.setEventSummary(req.eventSummary);
        if (existing.getExtractTs() == null) existing.setExtractTs(System.currentTimeMillis());
        newsMapper.updateById(existing);
        return ApiResponse.success(existing);
    }

    /** 单条重新调 LLM 分类。 */
    @PostMapping("/{id}/reclassify")
    public ApiResponse<EventResult> reclassify(@PathVariable Long id) {
        WebSearchNews n = newsMapper.selectById(id);
        if (n == null) return ApiResponse.error(404, "not found");
        List<EventResult> r = agent.classifyBatch(
                List.of(new NewsLite(n.getId(), n.getTitle(), n.getContent())));
        if (r.isEmpty()) return ApiResponse.error(500, "LLM 调用失败");
        EventResult er = r.get(0);
        newsMapper.updateEventFields(er.id, er.eventType, er.importance,
                er.eventSummary, er.models, System.currentTimeMillis());
        return ApiResponse.success(er);
    }

    @lombok.Data
    public static class UpdateReq {
        private String eventType;
        private Integer importance;
        private String eventSummary;
    }
}
