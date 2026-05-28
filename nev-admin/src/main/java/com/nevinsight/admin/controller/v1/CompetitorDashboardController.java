package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.CompetitorDimensionScore;
import com.nevinsight.model.mapper.core.CompetitorDimensionScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/competitor-dashboard")
@RequiredArgsConstructor
public class CompetitorDashboardController {

    private final CompetitorDimensionScoreMapper mapper;

    @GetMapping
    public ApiResponse<List<CompetitorDimensionScore>> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(mapper.selectList(
                new QueryWrapper<CompetitorDimensionScore>()
                        .eq("report_date", date)
                        .orderByAsc("brand_name", "dimension")));
    }

    @GetMapping("/brand/{brand}")
    public ApiResponse<List<CompetitorDimensionScore>> getByBrand(
            @PathVariable String brand,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(mapper.selectList(
                new QueryWrapper<CompetitorDimensionScore>()
                        .eq("brand_name", brand)
                        .eq("report_date", date)));
    }
}
