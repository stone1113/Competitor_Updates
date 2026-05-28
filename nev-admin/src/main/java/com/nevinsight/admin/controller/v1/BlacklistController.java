package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.NewsUrlBlacklist;
import com.nevinsight.model.mapper.core.NewsUrlBlacklistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 新闻 URL 黑名单管理：让运营手动维护过滤规则。
 *
 * 添加 / 启停 / 删除一个 pattern 后，下一次 collectByCategory 会立即生效（无须重启）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/blacklist")
@RequiredArgsConstructor
public class BlacklistController {

    private final NewsUrlBlacklistMapper mapper;

    @GetMapping
    public ApiResponse<List<NewsUrlBlacklist>> list(@RequestParam(required = false) Boolean enabledOnly) {
        LambdaQueryWrapper<NewsUrlBlacklist> qw = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(enabledOnly)) qw.eq(NewsUrlBlacklist::getIsEnabled, true);
        qw.orderByDesc(NewsUrlBlacklist::getIsEnabled).orderByAsc(NewsUrlBlacklist::getId);
        return ApiResponse.success(mapper.selectList(qw));
    }

    @PostMapping
    public ApiResponse<NewsUrlBlacklist> create(@RequestBody NewsUrlBlacklist req) {
        if (req.getPattern() == null || req.getPattern().trim().isEmpty())
            return ApiResponse.error(400, "pattern is required");
        req.setPattern(req.getPattern().trim());
        if (req.getIsEnabled() == null) req.setIsEnabled(true);
        try {
            mapper.insert(req);
        } catch (Exception e) {
            return ApiResponse.error(409, "duplicate or invalid: " + e.getMessage());
        }
        return ApiResponse.success(req);
    }

    @PutMapping("/{id}")
    public ApiResponse<NewsUrlBlacklist> update(@PathVariable Long id, @RequestBody NewsUrlBlacklist req) {
        NewsUrlBlacklist existing = mapper.selectById(id);
        if (existing == null) return ApiResponse.error(404, "not found");
        if (req.getPattern() != null) existing.setPattern(req.getPattern().trim());
        if (req.getReason() != null) existing.setReason(req.getReason());
        if (req.getIsEnabled() != null) existing.setIsEnabled(req.getIsEnabled());
        mapper.updateById(existing);
        return ApiResponse.success(existing);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }
}
