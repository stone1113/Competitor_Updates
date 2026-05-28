package com.nevinsight.intelligence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nevinsight.intelligence.client.RagflowClient;
import com.nevinsight.model.entity.core.AutohomeSeriesConfig;
import com.nevinsight.model.entity.core.AutohomeSpec;
import com.nevinsight.model.mapper.core.AutohomeSeriesConfigMapper;
import com.nevinsight.model.mapper.core.AutohomeSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 把 autohome_spec 表里的最新参数导出成 .xlsx，上传到 RAGFlow，让 LLM 检索时能命中。
 *
 * 一份 sheet = 一个车系，第一行表头，每行 (param_category, param_name, 车款A 值, 车款B 值, ...)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutohomeKnowledgeSyncService {

    public static final String EXPORT_FILENAME = "汽车之家车型参数.xlsx";

    private final AutohomeSeriesConfigMapper seriesConfigMapper;
    private final AutohomeSpecMapper specMapper;

    @Autowired(required = false)
    private RagflowClient ragflowClient;

    /** 把当前所有 enabled 车系的最新参数导出 .xlsx 并 upsert 到 RAGFlow。 */
    public Result exportAndUpload() {
        if (ragflowClient == null || !ragflowClient.isConfigured()) {
            log.warn("[AutohomeKB] RAGFlow not configured; skip upload");
            return new Result(false, 0, "RAGFlow 未配置");
        }

        List<AutohomeSeriesConfig> configs = seriesConfigMapper.selectList(
                new LambdaQueryWrapper<AutohomeSeriesConfig>()
                        .eq(AutohomeSeriesConfig::getIsEnabled, true));
        if (configs.isEmpty()) {
            return new Result(false, 0, "无启用车系");
        }

        byte[] xlsx;
        int sheetsWritten;
        try {
            ExportResult er = buildWorkbook(configs);
            xlsx = er.bytes;
            sheetsWritten = er.sheetsWritten;
        } catch (IOException e) {
            log.error("[AutohomeKB] xlsx build failed: {}", e.getMessage(), e);
            return new Result(false, 0, "xlsx 生成失败: " + e.getMessage());
        }

        if (sheetsWritten == 0) {
            return new Result(false, 0, "无可导出数据（autohome_spec 为空，先跑 AutohomeSyncService）");
        }

        String docId = ragflowClient.upsertDocument(xlsx, EXPORT_FILENAME);
        if (docId == null) {
            return new Result(false, sheetsWritten, "RAGFlow 上传失败");
        }
        log.info("[AutohomeKB] uploaded {} ({} sheets) → doc_id={}",
                EXPORT_FILENAME, sheetsWritten, docId);
        return new Result(true, sheetsWritten, "ok");
    }

    private ExportResult buildWorkbook(List<AutohomeSeriesConfig> configs) throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        CellStyle header = headerStyle(wb);
        int writtenSheets = 0;

        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        // 顶部加一张「索引」sheet
        Sheet idx = wb.createSheet("索引");
        idx.createRow(0).createCell(0).setCellValue("生成日期：" + today);
        idx.createRow(1).createCell(0).setCellValue("说明：每张 sheet 一个车系，列为车款，行为参数项");
        Row idxHeader = idx.createRow(3);
        idxHeader.createCell(0).setCellValue("车系名");
        idxHeader.createCell(1).setCellValue("品牌");
        idxHeader.createCell(2).setCellValue("角色");
        idxHeader.createCell(3).setCellValue("车款数");
        idxHeader.createCell(4).setCellValue("参数项数");
        for (int i = 0; i < 5; i++) idxHeader.getCell(i).setCellStyle(header);
        int idxRow = 4;

        for (AutohomeSeriesConfig cfg : configs) {
            List<AutohomeSpec> specs = specMapper.findLatestBySeriesId(cfg.getSeriesId());
            if (specs.isEmpty()) {
                log.info("[AutohomeKB] series={} model={} no specs in DB; skip",
                        cfg.getSeriesId(), cfg.getModelName());
                continue;
            }
            String sheetName = sanitizeSheetName(cfg.getModelName() + "-" + cfg.getSeriesId());
            Sheet sheet = wb.createSheet(sheetName);
            int paramCount = writeSeriesSheet(sheet, specs, header, cfg);
            int specCount = (int) specs.stream().map(AutohomeSpec::getSpecId).distinct().count();
            writtenSheets++;

            // 写索引
            Row r = idx.createRow(idxRow++);
            r.createCell(0).setCellValue(cfg.getModelName());
            r.createCell(1).setCellValue(cfg.getBrandName());
            r.createCell(2).setCellValue(cfg.getRole());
            r.createCell(3).setCellValue(specCount);
            r.createCell(4).setCellValue(paramCount);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        wb.close();
        return new ExportResult(bos.toByteArray(), writtenSheets);
    }

    /** 一张 sheet：行 = 参数项；列 = 车款。 */
    private int writeSeriesSheet(Sheet sheet, List<AutohomeSpec> specs, CellStyle header,
                                  AutohomeSeriesConfig cfg) {
        // 收集车款（保持出现顺序）
        LinkedHashMap<String, String> specIdToName = new LinkedHashMap<>();
        for (AutohomeSpec s : specs) {
            specIdToName.putIfAbsent(s.getSpecId(), s.getSpecName());
        }

        // 按 (category, param_name) 分组 → spec_id → value
        LinkedHashMap<String, LinkedHashMap<String, Map<String, String>>> grouped = new LinkedHashMap<>();
        for (AutohomeSpec s : specs) {
            grouped.computeIfAbsent(safe(s.getParamCategory()), k -> new LinkedHashMap<>())
                   .computeIfAbsent(safe(s.getParamName()), k -> new LinkedHashMap<>())
                   .put(s.getSpecId(), safe(s.getParamValue()));
        }

        // 表头 — 第一行：车系 + 抓取日期
        Row meta = sheet.createRow(0);
        meta.createCell(0).setCellValue("车系: " + cfg.getModelName() + " (" + cfg.getBrandName() + " · "
                + cfg.getRole() + " · series=" + cfg.getSeriesId() + ")");

        Row head = sheet.createRow(2);
        head.createCell(0).setCellValue("参数类别");
        head.createCell(1).setCellValue("参数项");
        int col = 2;
        for (String sname : specIdToName.values()) {
            head.createCell(col++).setCellValue(sname);
        }
        for (int i = 0; i < col; i++) {
            if (head.getCell(i) != null) head.getCell(i).setCellStyle(header);
        }

        int rowIdx = 3;
        int paramCount = 0;
        for (Map.Entry<String, LinkedHashMap<String, Map<String, String>>> catEntry : grouped.entrySet()) {
            String category = catEntry.getKey();
            for (Map.Entry<String, Map<String, String>> paramEntry : catEntry.getValue().entrySet()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(category);
                row.createCell(1).setCellValue(paramEntry.getKey());
                Map<String, String> valMap = paramEntry.getValue();
                int c = 2;
                for (String sid : specIdToName.keySet()) {
                    row.createCell(c++).setCellValue(valMap.getOrDefault(sid, ""));
                }
                paramCount++;
            }
        }

        for (int i = 0; i < col; i++) {
            sheet.setColumnWidth(i, 5000);
        }
        return paramCount;
    }

    private CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    /** Excel sheet 名 ≤ 31 字符、不能含 :\/?*[] */
    private String sanitizeSheetName(String name) {
        String s = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static class Result {
        public final boolean success;
        public final int sheetsWritten;
        public final String message;

        public Result(boolean success, int sheetsWritten, String message) {
            this.success = success;
            this.sheetsWritten = sheetsWritten;
            this.message = message;
        }
    }

    private static class ExportResult {
        final byte[] bytes;
        final int sheetsWritten;
        ExportResult(byte[] bytes, int sheetsWritten) {
            this.bytes = bytes;
            this.sheetsWritten = sheetsWritten;
        }
    }
}
