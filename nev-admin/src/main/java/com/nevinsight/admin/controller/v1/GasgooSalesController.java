package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.collector.service.GasgooSalesSyncService;
import com.nevinsight.collector.service.GasgooSalesSyncService.Result;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.GasgooSalesRecord;
import com.nevinsight.model.mapper.core.GasgooSalesRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/gasgoo")
@RequiredArgsConstructor
public class GasgooSalesController {

    private final GasgooSalesSyncService syncService;
    private final GasgooSalesRecordMapper salesMapper;

    /** 全量同步：所有已配 gasgoo_series_id 的车系，抓近 lookbackMonths 月。 */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> syncAll(
            @RequestParam(defaultValue = "3") int lookbackMonths) {
        log.info("[GasgooController] sync all lookback={}m", lookbackMonths);
        Result r = syncService.syncAll(lookbackMonths);
        Map<String, Object> data = new HashMap<>();
        data.put("seriesCount", r.seriesCount);
        data.put("rowsWritten", r.rowsWritten);
        data.put("failCount", r.failCount);
        data.put("errors", r.errors);
        return ApiResponse.success(data);
    }

    /** 单车系同步（前端「立即抓取销量」按钮用）。 */
    @PostMapping("/sync/{configId}")
    public ApiResponse<Map<String, Object>> syncOne(
            @PathVariable Long configId,
            @RequestParam(defaultValue = "3") int lookbackMonths) {
        log.info("[GasgooController] sync one configId={} lookback={}m", configId, lookbackMonths);
        try {
            int n = syncService.syncByConfigId(configId, lookbackMonths);
            Map<String, Object> data = new HashMap<>();
            data.put("rowsWritten", n);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[GasgooController] sync one failed", e);
            return ApiResponse.error(500, "sync failed: " + e.getMessage());
        }
    }

    /** 查每车型最新一条销量记录 — 供前端/卡片用。 */
    @GetMapping("/latest")
    public ApiResponse<List<GasgooSalesRecord>> latest() {
        return ApiResponse.success(salesMapper.findLatestByModel());
    }

    /** 按品牌/车型/年月 查询。 */
    @GetMapping
    public ApiResponse<List<GasgooSalesRecord>> list(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LambdaQueryWrapper<GasgooSalesRecord> qw = new LambdaQueryWrapper<>();
        if (brand != null && !brand.isEmpty()) qw.eq(GasgooSalesRecord::getBrandName, brand);
        if (model != null && !model.isEmpty()) qw.eq(GasgooSalesRecord::getModelName, model);
        if (year != null) qw.eq(GasgooSalesRecord::getPeriodYear, year);
        if (month != null) qw.eq(GasgooSalesRecord::getPeriodMonth, month);
        qw.orderByDesc(GasgooSalesRecord::getPeriodYear)
          .orderByDesc(GasgooSalesRecord::getPeriodMonth)
          .orderByAsc(GasgooSalesRecord::getBrandName);
        return ApiResponse.success(salesMapper.selectList(qw));
    }
}
