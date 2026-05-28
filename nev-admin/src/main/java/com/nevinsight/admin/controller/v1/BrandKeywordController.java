package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.BrandKeywordConfig;
import com.nevinsight.model.mapper.core.BrandKeywordConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/brand-keywords")
@RequiredArgsConstructor
public class BrandKeywordController {

    private final BrandKeywordConfigMapper mapper;

    /** List all keywords, optionally filtered by brand/category/enabled flag. */
    @GetMapping
    public ApiResponse<List<BrandKeywordConfig>> list(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabledOnly) {
        QueryWrapper<BrandKeywordConfig> qw = new QueryWrapper<>();
        if (brand != null && !brand.isEmpty()) qw.eq("brand_name", brand);
        if (category != null && !category.isEmpty()) qw.eq("category", category);
        if (Boolean.TRUE.equals(enabledOnly)) qw.eq("enabled", 1);
        qw.orderByAsc("category").orderByAsc("brand_name").orderByAsc("id");
        return ApiResponse.success(mapper.selectList(qw));
    }

    /** Distinct brand list (only category=brand) — 社交爬虫触发表单用 */
    @GetMapping("/brands")
    public ApiResponse<List<String>> brands() {
        QueryWrapper<BrandKeywordConfig> qw = new QueryWrapper<>();
        qw.select("DISTINCT brand_name")
          .eq("enabled", 1)
          .eq("category", "brand")
          .orderByAsc("brand_name");
        List<Object> rows = mapper.selectObjs(qw);
        List<String> out = new java.util.ArrayList<>();
        for (Object r : rows) if (r != null) out.add(String.valueOf(r));
        return ApiResponse.success(out);
    }

    @PostMapping
    public ApiResponse<BrandKeywordConfig> create(@RequestBody BrandKeywordConfig req) {
        if (req.getBrandName() == null || req.getBrandName().isEmpty())
            return ApiResponse.error(400, "brandName is required");
        if (req.getKeyword() == null || req.getKeyword().isEmpty())
            return ApiResponse.error(400, "keyword is required");
        if (req.getCategory() == null || req.getCategory().isEmpty()) req.setCategory("brand");
        if (req.getEnabled() == null) req.setEnabled(true);
        try {
            mapper.insert(req);
        } catch (Exception e) {
            return ApiResponse.error(409, "duplicate or invalid: " + e.getMessage());
        }
        return ApiResponse.success(req);
    }

    @PutMapping("/{id}")
    public ApiResponse<BrandKeywordConfig> update(@PathVariable Long id, @RequestBody BrandKeywordConfig req) {
        BrandKeywordConfig existing = mapper.selectById(id);
        if (existing == null) return ApiResponse.error(404, "not found");
        if (req.getBrandName() != null) existing.setBrandName(req.getBrandName());
        if (req.getKeyword() != null) existing.setKeyword(req.getKeyword());
        if (req.getCategory() != null) existing.setCategory(req.getCategory());
        if (req.getEnabled() != null) existing.setEnabled(req.getEnabled());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        mapper.updateById(existing);
        return ApiResponse.success(existing);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }

    /** Bulk replace all keywords for a brand within a given category. */
    @PostMapping("/bulk-replace")
    public ApiResponse<List<BrandKeywordConfig>> bulkReplace(@RequestBody BulkReplaceReq req) {
        if (req.brand == null || req.brand.isEmpty())
            return ApiResponse.error(400, "brand is required");
        if (req.keywords == null) req.keywords = java.util.Collections.emptyList();
        String category = (req.category == null || req.category.isEmpty()) ? "brand" : req.category;

        QueryWrapper<BrandKeywordConfig> qw = new QueryWrapper<>();
        qw.eq("brand_name", req.brand).eq("category", category);
        mapper.delete(qw);

        List<BrandKeywordConfig> created = new java.util.ArrayList<>();
        for (String kw : req.keywords) {
            String trimmed = kw == null ? "" : kw.trim();
            if (trimmed.isEmpty()) continue;
            BrandKeywordConfig c = new BrandKeywordConfig();
            c.setBrandName(req.brand);
            c.setKeyword(trimmed);
            c.setCategory(category);
            c.setEnabled(true);
            mapper.insert(c);
            created.add(c);
        }
        return ApiResponse.success(created);
    }

    @lombok.Data
    public static class BulkReplaceReq {
        private String brand;
        private String category;
        private List<String> keywords;
    }
}
