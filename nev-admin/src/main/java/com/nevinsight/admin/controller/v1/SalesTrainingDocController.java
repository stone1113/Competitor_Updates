package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.SalesTrainingDoc;
import com.nevinsight.model.mapper.core.SalesTrainingDocMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 销售培训文档 REST。给 RAGFlow Agent MCP 工具回写用 + 前端列表/详情展示。
 *
 * 端点：
 *   POST   /api/v1/sales-training-doc          创建（MCP create_sales_doc 工具调）
 *   GET    /api/v1/sales-training-doc          列表（带分页 + target_model 过滤）
 *   GET    /api/v1/sales-training-doc/{id}     详情（view_count +1）
 *   DELETE /api/v1/sales-training-doc/{id}     删除
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sales-training-doc")
@RequiredArgsConstructor
public class SalesTrainingDocController {

    private final SalesTrainingDocMapper mapper;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody SalesTrainingDoc req) {
        if (req.getTitle() == null || req.getTitle().isEmpty())
            return ApiResponse.error(400, "title 不能为空");
        if (req.getContentMd() == null || req.getContentMd().isEmpty())
            return ApiResponse.error(400, "contentMd 不能为空");
        if (req.getTargetModel() == null || req.getTargetModel().isEmpty())
            return ApiResponse.error(400, "targetModel 不能为空");

        if (req.getGenerator() == null) req.setGenerator("ragflow-agent");
        if (req.getStatus() == null) req.setStatus(1);
        req.setViewCount(0);
        mapper.insert(req);
        log.info("[SalesTrainingDoc] created id={} title={} target={}",
                req.getId(), req.getTitle(), req.getTargetModel());

        Map<String, Object> out = new HashMap<>();
        out.put("id", req.getId());
        out.put("title", req.getTitle());
        out.put("viewUrl", "/sales-training/" + req.getId());
        return ApiResponse.success(out);
    }

    @GetMapping
    public ApiResponse<IPage<SalesTrainingDoc>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String targetModel) {
        LambdaQueryWrapper<SalesTrainingDoc> qw = new LambdaQueryWrapper<>();
        if (targetModel != null && !targetModel.isEmpty()) {
            qw.eq(SalesTrainingDoc::getTargetModel, targetModel);
        }
        qw.eq(SalesTrainingDoc::getStatus, 1);
        qw.orderByDesc(SalesTrainingDoc::getAddTs);
        // 列表不返回 contentMd（太大），用 select 排除
        qw.select(SalesTrainingDoc.class, info -> !"content_md".equals(info.getColumn()));
        return ApiResponse.success(mapper.selectPage(new Page<>(page, size), qw));
    }

    @GetMapping("/{id}")
    public ApiResponse<SalesTrainingDoc> detail(@PathVariable Long id) {
        SalesTrainingDoc doc = mapper.selectById(id);
        if (doc == null) return ApiResponse.error(404, "文档不存在");
        // view_count + 1
        SalesTrainingDoc upd = new SalesTrainingDoc();
        upd.setId(id);
        upd.setViewCount((doc.getViewCount() == null ? 0 : doc.getViewCount()) + 1);
        mapper.updateById(upd);
        return ApiResponse.success(doc);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success(null);
    }
}
