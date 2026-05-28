package com.nevinsight.intelligence.agent;

import com.nevinsight.intelligence.service.QwenAiService;
import com.nevinsight.model.entity.core.DeepBenchmarkMessage;
import com.nevinsight.model.entity.core.DeepBenchmarkSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v9.5 多轮对话追问 Agent。
 *
 * 输入：用户追问（如「电池详细对比」「为啥销量这么低」）+ 历史会话上下文（首轮 5 分析师 JSON + 之前对话）
 * 输出：Markdown 回答（也可含 mermaid / chart 代码块）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowupAgent {

    /** 历史拼接最大字符数（防超 prompt 限） */
    private static final int MAX_CONTEXT_CHARS = 12000;

    private final QwenAiService qwenAiService;

    public String answer(DeepBenchmarkSession session, List<DeepBenchmarkMessage> history, String userQuestion) {
        long start = System.currentTimeMillis();
        String systemPrompt = buildSystemPrompt(session);
        String userPrompt = buildUserPrompt(history, userQuestion);
        try {
            String resp = qwenAiService.chatWithReportModelHttp(systemPrompt, userPrompt);
            String md = stripFence(resp);
            log.info("[FollowupAgent] 答复完成 len={} elapsed={}ms",
                    md.length(), System.currentTimeMillis() - start);
            return md;
        } catch (Exception e) {
            log.error("[FollowupAgent] 调用失败: {}", e.getMessage(), e);
            return "⚠ 追问回答失败：" + e.getMessage();
        }
    }

    private String buildSystemPrompt(DeepBenchmarkSession s) {
        return "你是「" + s.getSelfModel() + " vs " + s.getCompetitorModels() + "」深度对标的首席分析师。\n\n" +
                "【对话规则】\n" +
                "1. 用户已经看过你的首轮 5 维度深度分析报告（POWER / BODY / OFFROAD / PRICE / SALES）\n" +
                "2. 现在用户追问 — 你要基于历史上下文回答\n" +
                "3. 回答用 Markdown 格式，保持专业、含具体数字\n" +
                "4. 如果适合图表，可输出 mermaid radar / chart 代码块：\n" +
                "   ```chart\n" +
                "   {\"type\":\"bar\",\"data\":{\"labels\":[\"M817\",\"豹5\"],\"datasets\":[{\"label\":\"4月销量\",\"data\":[866,5511]}]}}\n" +
                "   ```\n" +
                "5. 不知道时直接说「数据缺失」，不要编造\n" +
                "6. 回答控制在 300-600 字\n";
    }

    private String buildUserPrompt(List<DeepBenchmarkMessage> history, String userQuestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("【历史对话】\n");
        int budget = MAX_CONTEXT_CHARS;
        for (DeepBenchmarkMessage m : history) {
            String c = m.getContent() == null ? "" : m.getContent();
            if (c.length() > 4000) c = c.substring(0, 4000) + "…";
            String chunk = "[" + m.getRole() + "] " + c + "\n\n";
            if (chunk.length() > budget) break;
            sb.append(chunk);
            budget -= chunk.length();
        }
        sb.append("\n【新追问】\n").append(userQuestion);
        sb.append("\n\n请基于上述上下文回答。");
        return sb.toString();
    }

    private String stripFence(String s) {
        if (s == null) return "";
        return s.trim()
                .replaceAll("^```markdown\\s*", "")
                .replaceAll("^```md\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();
    }
}
