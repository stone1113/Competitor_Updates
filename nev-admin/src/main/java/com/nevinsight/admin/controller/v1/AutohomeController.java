package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.collector.service.AutohomeSyncService;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.AutohomeSpec;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.AutohomeSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 汽车之家车系配置 + 参数浏览 API。
 *
 * 路由前缀：/api/v1/autohome
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/autohome")
@RequiredArgsConstructor
public class AutohomeController {

    private final AutohomeSeriesConfigMapper configMapper;
    private final AutohomeSpecMapper specMapper;
    private final AutohomeSyncService syncService;

    // ===== series config CRUD =====

    @GetMapping("/series")
    public ApiResponse<List<SeriesView>> listSeries(@RequestParam(required = false) String role) {
        LambdaQueryWrapper<AutohomeSeriesConfig> qw = new LambdaQueryWrapper<>();
        if (role != null && !role.isEmpty()) qw.eq(AutohomeSeriesConfig::getRole, role);
        qw.orderByAsc(AutohomeSeriesConfig::getRole)
          .orderByAsc(AutohomeSeriesConfig::getBrandName)
          .orderByAsc(AutohomeSeriesConfig::getId);
        List<AutohomeSeriesConfig> cfgs = configMapper.selectList(qw);

        List<SeriesView> out = new ArrayList<>();
        for (AutohomeSeriesConfig c : cfgs) {
            SeriesView v = new SeriesView();
            v.id = c.getId();
            v.brandName = c.getBrandName();
            v.modelName = c.getModelName();
            v.seriesId = c.getSeriesId();
            v.role = c.getRole();
            v.vsSelfModel = c.getVsSelfModel();
            v.isEnabled = c.getIsEnabled();
            v.remark = c.getRemark();
            v.lastCrawlDate = specMapper.findLastCrawlDate(c.getSeriesId());
            v.latestParamCount = specMapper.countLatestBySeriesId(c.getSeriesId());
            out.add(v);
        }
        return ApiResponse.success(out);
    }

    @PostMapping("/series")
    public ApiResponse<AutohomeSeriesConfig> createSeries(@RequestBody AutohomeSeriesConfig req) {
        if (req.getSeriesId() == null || req.getSeriesId().isEmpty())
            return ApiResponse.error(400, "series_id is required");
        if (req.getBrandName() == null || req.getBrandName().isEmpty())
            return ApiResponse.error(400, "brand_name is required");
        if (req.getModelName() == null || req.getModelName().isEmpty())
            return ApiResponse.error(400, "model_name is required");
        if (req.getRole() == null || req.getRole().isEmpty()) req.setRole("competitor");
        if (req.getIsEnabled() == null) req.setIsEnabled(true);
        try {
            configMapper.insert(req);
        } catch (Exception e) {
            return ApiResponse.error(409, "duplicate or invalid: " + e.getMessage());
        }
        return ApiResponse.success(req);
    }

    @PutMapping("/series/{id}")
    public ApiResponse<AutohomeSeriesConfig> updateSeries(@PathVariable Long id,
                                                          @RequestBody AutohomeSeriesConfig req) {
        AutohomeSeriesConfig existing = configMapper.selectById(id);
        if (existing == null) return ApiResponse.error(404, "not found");
        if (req.getBrandName() != null) existing.setBrandName(req.getBrandName());
        if (req.getModelName() != null) existing.setModelName(req.getModelName());
        if (req.getSeriesId() != null) existing.setSeriesId(req.getSeriesId());
        if (req.getRole() != null) existing.setRole(req.getRole());
        if (req.getVsSelfModel() != null) {
            existing.setVsSelfModel(req.getVsSelfModel().isEmpty() ? null : req.getVsSelfModel());
        }
        if (req.getIsEnabled() != null) existing.setIsEnabled(req.getIsEnabled());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        configMapper.updateById(existing);
        return ApiResponse.success(existing);
    }

    @DeleteMapping("/series/{id}")
    public ApiResponse<Void> deleteSeries(@PathVariable Long id) {
        configMapper.deleteById(id);
        return ApiResponse.success();
    }

    /** 单车系手动抓取（前端「立刻抓取」按钮）。同步阻塞，单车系约 5-15 秒。 */
    @PostMapping("/sync/{seriesId}")
    public ApiResponse<AutohomeSyncService.SyncResult> syncOne(@PathVariable String seriesId) {
        log.info("[Autohome] manual sync seriesId={}", seriesId);
        return ApiResponse.success(syncService.syncOne(seriesId));
    }

    /** 全量抓取（异步意义：约 1-3 分钟）。 */
    @PostMapping("/sync-all")
    public ApiResponse<AutohomeSyncService.SyncResult> syncAll() {
        log.info("[Autohome] manual sync-all");
        return ApiResponse.success(syncService.syncAll());
    }

    /**
     * 按本品车型一键拉对标桌（本品 + 该桌所有 vs_self_model 匹配的竞品）。
     * 用于前端 BenchmarkH5View — 飞书卡片按钮直跳。
     */
    @GetMapping("/benchmark/by-self")
    public ApiResponse<SpecComparison> benchmarkBySelf(@RequestParam String selfModel) {
        if (selfModel == null || selfModel.isEmpty()) {
            return ApiResponse.error(400, "selfModel is required");
        }
        // 该桌的 series_id 序列：先 self 再竞品
        List<AutohomeSeriesConfig> selfCfgs = configMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .eq(AutohomeSeriesConfig::getModelName, selfModel)
                        .eq(AutohomeSeriesConfig::getRole, "self"));
        List<AutohomeSeriesConfig> competitorCfgs = configMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .eq(AutohomeSeriesConfig::getVsSelfModel, selfModel)
                        .orderByAsc(AutohomeSeriesConfig::getId));

        List<String> ids = new ArrayList<>();
        for (AutohomeSeriesConfig c : selfCfgs) ids.add(c.getSeriesId());
        for (AutohomeSeriesConfig c : competitorCfgs) ids.add(c.getSeriesId());
        if (ids.isEmpty()) {
            return ApiResponse.success(SpecComparison.empty());
        }
        return compareSpecs(String.join(",", ids));
    }

    // ===== spec viewer =====

    /**
     * 取多车系最新参数，前端做并列对比。
     * 返回结构：{ series: [{seriesId, modelName, brandName, specName}],
     *           categories: [{category, params: [{name, values: {seriesId -> value}}]}] }
     */
    @GetMapping("/specs")
    public ApiResponse<SpecComparison> compareSpecs(@RequestParam String seriesIds) {
        String[] ids = seriesIds.split(",");
        if (ids.length == 0) return ApiResponse.success(SpecComparison.empty());

        SpecComparison comp = new SpecComparison();
        // 收集每个 series 的车款代表名（取最新 crawl_date 下 spec_id 字典序最大者）
        Map<String, AutohomeSeriesConfig> sidToCfg = new LinkedHashMap<>();
        for (String sid : ids) {
            String trimmed = sid.trim();
            if (trimmed.isEmpty()) continue;
            AutohomeSeriesConfig cfg = configMapper.selectOne(
                    new LambdaQueryWrapper<AutohomeSeriesConfig>().eq(AutohomeSeriesConfig::getSeriesId, trimmed));
            if (cfg != null) sidToCfg.put(trimmed, cfg);
        }

        // 每 series：取最新一次抓取的全部参数；每 series 内只取「主代表车款」
        // 用 (param_category, param_name) 作 key，跨 series 聚合 value
        Map<String, Map<String, Map<String, String>>> grouped = new LinkedHashMap<>();
        // category -> paramName -> seriesId -> value

        Map<String, SeriesView> seriesHead = new LinkedHashMap<>();
        for (Map.Entry<String, AutohomeSeriesConfig> e : sidToCfg.entrySet()) {
            String sid = e.getKey();
            AutohomeSeriesConfig cfg = e.getValue();
            List<AutohomeSpec> specs = specMapper.findLatestBySeriesId(sid);

            // 选 representative spec_id（取 spec_id 字典序最大）
            String repSpecId = specs.stream().map(AutohomeSpec::getSpecId)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            String repSpecName = "";
            if (repSpecId != null) {
                repSpecName = specs.stream()
                        .filter(s -> repSpecId.equals(s.getSpecId()))
                        .map(AutohomeSpec::getSpecName)
                        .filter(Objects::nonNull).findFirst().orElse("");
            }

            SeriesView head = new SeriesView();
            head.id = cfg.getId();
            head.seriesId = sid;
            head.brandName = cfg.getBrandName();
            head.modelName = cfg.getModelName();
            head.role = cfg.getRole();
            head.specName = repSpecName;
            seriesHead.put(sid, head);

            if (repSpecId == null) continue;
            for (AutohomeSpec s : specs) {
                if (!repSpecId.equals(s.getSpecId())) continue;
                grouped.computeIfAbsent(safe(s.getParamCategory()), k -> new LinkedHashMap<>())
                       .computeIfAbsent(safe(s.getParamName()), k -> new LinkedHashMap<>())
                       .put(sid, safe(s.getParamValue()));
            }
        }

        comp.series = new ArrayList<>(seriesHead.values());
        comp.categories = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> catEntry : grouped.entrySet()) {
            String catName = catEntry.getKey();
            CategoryGroup cg = new CategoryGroup();
            cg.category = catName;
            cg.params = catEntry.getValue().entrySet().stream()
                    .map(p -> {
                        ParamRow pr = new ParamRow();
                        // v8+: 还原 autohome 反爬替换的 · 占位
                        pr.name = com.nevinsight.model.util.AutohomeParamNormalizer
                                .normalize(catName, p.getKey());
                        pr.values = p.getValue();
                        return pr;
                    })
                    // v8+: 过滤所有 series 都是空/-/·的无意义参数行
                    .filter(pr -> pr.values.values().stream()
                            .anyMatch(v -> !com.nevinsight.model.util.AutohomeParamNormalizer.isBlankValue(v)))
                    .collect(Collectors.toList());
            if (!cg.params.isEmpty()) {
                comp.categories.add(cg);
            }
        }
        return ApiResponse.success(comp);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // ===== DTOs =====

    @lombok.Data
    public static class SeriesView {
        private Long id;
        private String brandName;
        private String modelName;
        private String seriesId;
        private String role;
        private String vsSelfModel;
        private Boolean isEnabled;
        private String remark;
        private LocalDate lastCrawlDate;
        private Integer latestParamCount;
        /** 仅在 compareSpecs 返回时填充，代表此车系展示用的车款全名。 */
        private String specName;
    }

    @lombok.Data
    public static class SpecComparison {
        private List<SeriesView> series = new ArrayList<>();
        private List<CategoryGroup> categories = new ArrayList<>();

        public static SpecComparison empty() { return new SpecComparison(); }
    }

    @lombok.Data
    public static class CategoryGroup {
        private String category;
        private List<ParamRow> params = new ArrayList<>();
    }

    @lombok.Data
    public static class ParamRow {
        private String name;
        /** seriesId -> param_value */
        private Map<String, String> values = new LinkedHashMap<>();
    }
}
