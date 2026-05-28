package com.nevinsight.admin.controller.v1;

import com.nevinsight.common.ApiResponse;
import com.nevinsight.report.pipeline.DailyReportPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/report-pipeline")
@RequiredArgsConstructor
public class ReportPipelineController {

    private final DailyReportPipeline pipeline;

    @PostMapping("/generate")
    public ApiResponse<DailyReportPipeline.PipelineResult> generate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "true") boolean persist,
            @RequestParam(defaultValue = "false") boolean push) {
        DailyReportPipeline.PipelineResult result = pipeline.run(date, persist, push);
        return ApiResponse.success(result);
    }
}
