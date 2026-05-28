package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.DataSourceConfig;
import com.nevinsight.model.mapper.core.DataSourceConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-source")
@RequiredArgsConstructor
public class DataSourceConfigController {

    private final DataSourceConfigMapper mapper;

    @GetMapping
    public ApiResponse<List<DataSourceConfig>> list(
            @RequestParam(required = false) String sourceType) {
        QueryWrapper<DataSourceConfig> qw = new QueryWrapper<>();
        if (sourceType != null) qw.eq("source_type", sourceType);
        return ApiResponse.success(mapper.selectList(qw));
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody DataSourceConfig config) {
        mapper.insert(config);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody DataSourceConfig config) {
        config.setId(id);
        mapper.updateById(config);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }
}
