package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.KnowledgeDocument;
import com.nevinsight.model.mapper.core.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeDocController {

    private final KnowledgeDocumentMapper mapper;

    @GetMapping
    public ApiResponse<List<KnowledgeDocument>> list() {
        return ApiResponse.success(mapper.selectList(new QueryWrapper<KnowledgeDocument>()
                .orderByDesc("create_time")));
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody KnowledgeDocument doc) {
        mapper.insert(doc);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return ApiResponse.success();
    }
}
