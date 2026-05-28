package com.nevinsight.admin.controller.v1;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.PromptTemplate;
import com.nevinsight.model.mapper.core.PromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/prompt")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateMapper mapper;

    @GetMapping
    public ApiResponse<List<PromptTemplate>> list(
            @RequestParam(required = false) String agentName) {
        QueryWrapper<PromptTemplate> qw = new QueryWrapper<>();
        if (agentName != null) qw.eq("agent_name", agentName);
        qw.orderByDesc("version");
        return ApiResponse.success(mapper.selectList(qw));
    }

    @GetMapping("/active/{agentName}")
    public ApiResponse<PromptTemplate> getActive(@PathVariable String agentName) {
        return ApiResponse.success(mapper.findActiveByAgent(agentName));
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody PromptTemplate template) {
        mapper.insert(template);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody PromptTemplate template) {
        template.setId(id);
        mapper.updateById(template);
        return ApiResponse.success();
    }
}
