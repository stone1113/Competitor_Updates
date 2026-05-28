package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.WebSearchNews;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.WebSearchNewsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 按对标车型查最新金融政策帖（含 OCR 增强）。
 *
 * 单车型：GET /api/v1/finance/by-model?model=豹8&limit=5
 * 全 9 对标车型：GET /api/v1/finance/all-models?limit=3
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
public class FinanceByModelController {

    private final WebSearchNewsMapper newsMapper;
    private final AutohomeSeriesConfigMapper seriesConfigMapper;

    /** 单车型查询。 */
    @GetMapping("/by-model")
    public ApiResponse<Map<String, Object>> byModel(
            @RequestParam String model,
            @RequestParam(defaultValue = "5") int limit) {
        if (model == null || model.isEmpty()) {
            return ApiResponse.error(400, "model 不能为空");
        }
        String key = stripBrandPrefix(model);
        List<WebSearchNews> posts = newsMapper.findLatestFinanceByModel(key, limit);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("model", model);
        data.put("modelKey", key);
        data.put("count", posts.size());
        data.put("posts", posts.stream().map(this::toView).collect(java.util.stream.Collectors.toList()));
        return ApiResponse.success(data);
    }

    /** 全对标车型一次性查（按 role/vs_self_model 分组）。 */
    @GetMapping("/all-models")
    public ApiResponse<Map<String, Object>> allModels(
            @RequestParam(defaultValue = "3") int limit) {
        List<AutohomeSeriesConfig> cfgs = seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true)
                        .orderByDesc(AutohomeSeriesConfig::getRole)   // self 在前
                        .orderByAsc(AutohomeSeriesConfig::getVsSelfModel)
                        .orderByAsc(AutohomeSeriesConfig::getId));

        List<Map<String, Object>> modelGroups = new ArrayList<>();
        for (AutohomeSeriesConfig cfg : cfgs) {
            String model = cfg.getModelName();
            String key = stripBrandPrefix(model);
            List<WebSearchNews> posts = newsMapper.findLatestFinanceByModel(key, limit);
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("model", model);
            g.put("brand", cfg.getBrandName());
            g.put("role", cfg.getRole());
            g.put("vsSelfModel", cfg.getVsSelfModel());
            g.put("count", posts.size());
            g.put("posts", posts.stream().map(this::toView).collect(java.util.stream.Collectors.toList()));
            modelGroups.add(g);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groups", modelGroups);
        data.put("totalModels", cfgs.size());
        return ApiResponse.success(data);
    }

    /** 去掉品牌前缀方便模糊匹配（如「方程豹豹5」→「豹5」、「猛士M817」→「M817」），原值兜底也尝试。 */
    private static String stripBrandPrefix(String model) {
        if (model == null) return "";
        // 已知品牌前缀
        String[] prefixes = {"方程豹", "猛士汽车", "猛士", "仰望", "坦克", "问界", "路虎", "卫士"};
        for (String p : prefixes) {
            if (model.startsWith(p) && model.length() > p.length()) {
                return model.substring(p.length());
            }
        }
        return model;
    }

    private Map<String, Object> toView(WebSearchNews n) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", n.getId());
        v.put("brandName", n.getBrandName());
        v.put("eventType", n.getEventType());
        v.put("eventImportance", n.getEventImportance());
        v.put("eventSummary", n.getEventSummary());
        v.put("title", n.getTitle());
        v.put("url", n.getUrl());
        v.put("sourceTool", n.getSourceTool());
        v.put("imageUrls", n.getImageUrls());
        v.put("imageOcrText", n.getImageOcrText());
        v.put("ocrTs", n.getOcrTs());
        v.put("addTs", n.getAddTs());
        v.put("hasPolicy", n.getEventSummary() != null && n.getEventSummary().contains("图含："));
        return v;
    }
}
