package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.collector.service.OfficialAccountCrawlerService;
import com.nevinsight.collector.service.OfficialPostIngester;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.OfficialAccountConfig;
import com.nevinsight.model.mapper.core.OfficialAccountConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 官方账号配置 CRUD + 抓取触发 + ingest 触发。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/official-accounts")
@RequiredArgsConstructor
public class OfficialAccountController {

    private static final Set<String> ALLOWED_PLATFORMS = Set.of("wb", "dy", "xhs");

    private final OfficialAccountConfigMapper mapper;
    private final OfficialAccountCrawlerService crawlerService;
    private final OfficialPostIngester ingester;

    @GetMapping
    public ApiResponse<List<OfficialAccountConfig>> list(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String platform) {
        LambdaQueryWrapper<OfficialAccountConfig> qw = new LambdaQueryWrapper<>();
        if (brand != null && !brand.isEmpty()) qw.eq(OfficialAccountConfig::getBrandName, brand);
        if (platform != null && !platform.isEmpty()) qw.eq(OfficialAccountConfig::getPlatform, platform);
        qw.orderByAsc(OfficialAccountConfig::getPlatform)
          .orderByAsc(OfficialAccountConfig::getBrandName)
          .orderByAsc(OfficialAccountConfig::getId);
        return ApiResponse.success(mapper.selectList(qw));
    }

    @PostMapping
    public ApiResponse<OfficialAccountConfig> create(@RequestBody OfficialAccountConfig req) {
        if (req.getBrandName() == null || req.getBrandName().isEmpty())
            return ApiResponse.error(400, "brand_name is required");
        if (req.getPlatform() == null || !ALLOWED_PLATFORMS.contains(req.getPlatform()))
            return ApiResponse.error(400, "platform must be wb/dy/xhs");
        if (req.getAccountId() == null || req.getAccountId().isEmpty())
            return ApiResponse.error(400, "account_id is required");
        if (req.getIsEnabled() == null) req.setIsEnabled(true);
        try {
            mapper.insert(req);
        } catch (Exception e) {
            return ApiResponse.error(409, "duplicate or invalid: " + e.getMessage());
        }
        return ApiResponse.success(req);
    }

    @PutMapping("/{id}")
    public ApiResponse<OfficialAccountConfig> update(@PathVariable Long id,
                                                     @RequestBody OfficialAccountConfig req) {
        OfficialAccountConfig existing = mapper.selectById(id);
        if (existing == null) return ApiResponse.error(404, "not found");
        if (req.getBrandName() != null) existing.setBrandName(req.getBrandName());
        if (req.getPlatform() != null) {
            if (!ALLOWED_PLATFORMS.contains(req.getPlatform()))
                return ApiResponse.error(400, "platform must be wb/dy/xhs");
            existing.setPlatform(req.getPlatform());
        }
        if (req.getAccountId() != null) existing.setAccountId(req.getAccountId());
        if (req.getAccountName() != null) existing.setAccountName(req.getAccountName());
        if (req.getAccountUrl() != null) existing.setAccountUrl(req.getAccountUrl());
        if (req.getIsEnabled() != null) existing.setIsEnabled(req.getIsEnabled());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        mapper.updateById(existing);
        return ApiResponse.success(existing);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }

    /** 触发抓取（异步：crawler-service 后台跑）。 */
    @PostMapping("/crawl-now")
    public ApiResponse<OfficialAccountCrawlerService.CrawlResult> crawlNow(
            @RequestParam(required = false) String platform) {
        log.info("[OfficialAccount] manual crawl-now platform={}", platform);
        return ApiResponse.success(crawlerService.crawlByPlatform(platform));
    }

    /** 触发 ingest（把已落库的 weibo_note/douyin_aweme 中官号帖转写到 web_search_news）。 */
    @PostMapping("/ingest-now")
    public ApiResponse<Map<String, Object>> ingestNow() {
        log.info("[OfficialAccount] manual ingest-now");
        OfficialPostIngester.IngestResult r = ingester.ingestAll();
        Map<String, Object> out = new HashMap<>();
        out.put("scanned", r.scanned);
        out.put("inserted", r.inserted);
        return ApiResponse.success(out);
    }
}
