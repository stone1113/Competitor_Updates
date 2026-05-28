package com.nevinsight.admin.controller.v1;

import com.nevinsight.collector.service.AutohomeSyncService;
import com.nevinsight.common.ApiResponse;
import com.nevinsight.intelligence.config.RagflowProperties;
import com.nevinsight.intelligence.service.AutohomeKnowledgeSyncService;
import com.nevinsight.report.pipeline.CompetitorReportPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/competitor-report")
@RequiredArgsConstructor
public class CompetitorReportController {

    private final CompetitorReportPipeline pipeline;
    private final AutohomeSyncService autohomeSyncService;
    private final AutohomeKnowledgeSyncService autohomeKnowledgeSyncService;
    private final RagflowProperties ragflowProperties;

    @Value("${nevinsight.feishu.require-login:false}")
    private boolean feishuRequireLogin;

    @Value("${nevinsight.feishu.app-id:}")
    private String feishuAppId;

    /** 手动触发竞品分析日报（生成 + 可选推送）。 */
    @PostMapping("/generate")
    public ApiResponse<CompetitorReportPipeline.Result> generate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean push) {
        return ApiResponse.success(pipeline.run(date, push));
    }

    /** 手动触发汽车之家参数同步（抓 → 写 DB）。约 30-60 秒。 */
    @PostMapping("/sync-autohome")
    public ApiResponse<AutohomeSyncService.SyncResult> syncAutohome() {
        return ApiResponse.success(autohomeSyncService.syncAll());
    }

    /** 手动触发把 autohome_spec 导出 .xlsx 并 upsert 到 RAGFlow。 */
    @PostMapping("/upload-autohome-to-ragflow")
    public ApiResponse<AutohomeKnowledgeSyncService.Result> uploadAutohomeToRagflow() {
        return ApiResponse.success(autohomeKnowledgeSyncService.exportAndUpload());
    }

    /**
     * 返回 RAGFlow Chat Assistant 公开分享 URL（前端 iframe 嵌入 + 飞书按钮跳转用）。
     * 需要先在 RAGFlow UI 创建 Chat Assistant 并生成 share token，再把 token 写入
     * 环境变量 RAGFLOW_DEEP_ANALYSIS_SHARED_ID。
     */
    @GetMapping("/deep-analysis-url")
    public ApiResponse<Map<String, Object>> deepAnalysisUrl(
            @RequestParam(required = false) String userId) {
        Map<String, Object> out = new HashMap<>();
        String sharedId = ragflowProperties.getDeepAnalysisSharedId();
        String beta = ragflowProperties.getDeepAnalysisBetaToken();
        String from = ragflowProperties.getDeepAnalysisFrom();
        String uiBase = ragflowProperties.getUiBaseUrl();
        if (sharedId == null || sharedId.isEmpty() || beta == null || beta.isEmpty()) {
            out.put("configured", false);
            out.put("message", "未配置 RAGFLOW_DEEP_ANALYSIS_SHARED_ID 或 RAGFLOW_DEEP_ANALYSIS_BETA_TOKEN；请在 RAGFlow UI 打开 Chat/Agent → 嵌入网页，复制 src 中的 shared_id 与 auth 写入 .env");
            return ApiResponse.success(out);
        }
        // RAGFlow v0.25 嵌入 iframe 格式
        // - Chat Assistant: path=/chats/share  from=chat
        // - Agent Canvas:   path=/agent/share  from=agent
        if (from == null || from.isEmpty()) from = "agent";
        String path = "agent".equals(from) ? "/agent/share" : "/chats/share";
        StringBuilder urlBuilder = new StringBuilder();
        // v9.7 移动端优化：visible_avatar=1 隐藏头像（反逻辑），拓宽内容区
        urlBuilder.append(String.format(
                "%s%s?shared_id=%s&from=%s&auth=%s&theme=light&locale=zh&visible_avatar=1",
                uiBase, path, sharedId, from, beta));
        // per-user session：把飞书 openId 传给 RAGFlow（data_user_id）
        if (userId != null && !userId.isEmpty()) {
            urlBuilder.append("&data_user_id=").append(
                    java.net.URLEncoder.encode(userId, java.nio.charset.StandardCharsets.UTF_8));
        }
        String url = urlBuilder.toString();
        out.put("configured", true);
        out.put("url", url);
        // v9.7 飞书登录门禁：前端 H5 页据此决定是否要先跳飞书 OAuth
        out.put("requireFeishuLogin", feishuRequireLogin && !feishuAppId.isEmpty());
        out.put("feishuLoginUrl", "/api/v1/feishu-auth/login");
        out.put("loginMessage", "请用飞书账号登录后访问对标分析");
        return ApiResponse.success(out);
    }
}
