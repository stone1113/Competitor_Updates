package com.nevinsight.admin.controller.v1;

import com.nevinsight.common.ApiResponse;
import com.nevinsight.model.entity.core.TalkingPointVersion;
import com.nevinsight.model.mapper.core.TalkingPointVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/talking-point")
@RequiredArgsConstructor
public class TalkingPointVersionController {

    private final TalkingPointVersionMapper mapper;

    @GetMapping("/{talkingPointId}/versions")
    public ApiResponse<List<TalkingPointVersion>> listVersions(@PathVariable Long talkingPointId) {
        return ApiResponse.success(mapper.findAllVersions(talkingPointId));
    }

    @GetMapping("/{talkingPointId}/current")
    public ApiResponse<TalkingPointVersion> getCurrent(@PathVariable Long talkingPointId) {
        return ApiResponse.success(mapper.findCurrentVersion(talkingPointId));
    }

    @PostMapping("/{talkingPointId}/rollback/{version}")
    public ApiResponse<Void> rollback(@PathVariable Long talkingPointId, @PathVariable Integer version) {
        // 将当前版本标记为非当前
        TalkingPointVersion current = mapper.findCurrentVersion(talkingPointId);
        if (current != null) {
            current.setIsCurrent(false);
            mapper.updateById(current);
        }
        // 找到目标版本并标记为当前
        List<TalkingPointVersion> versions = mapper.findAllVersions(talkingPointId);
        for (TalkingPointVersion v : versions) {
            if (v.getVersion().equals(version)) {
                v.setIsCurrent(true);
                mapper.updateById(v);
                break;
            }
        }
        return ApiResponse.success();
    }
}
