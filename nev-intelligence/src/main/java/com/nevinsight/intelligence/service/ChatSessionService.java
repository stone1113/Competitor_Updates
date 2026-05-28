package com.nevinsight.intelligence.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nevinsight.model.entity.core.DeepBenchmarkMessage;
import com.nevinsight.model.entity.core.DeepBenchmarkSession;
import com.nevinsight.model.mapper.core.DeepBenchmarkMessageMapper;
import com.nevinsight.model.mapper.core.DeepBenchmarkSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * v9.5 对话式深度对标 — 会话 + 消息 CRUD 封装。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final DeepBenchmarkSessionMapper sessionMapper;
    private final DeepBenchmarkMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /** 创建新会话 */
    public DeepBenchmarkSession createSession(String userToken, String selfModel,
                                                List<String> competitorModels, String firstUserMessage) {
        DeepBenchmarkSession s = new DeepBenchmarkSession();
        s.setUserToken(userToken);
        s.setSelfModel(selfModel);
        s.setCompetitorModels(String.join(",", competitorModels));
        String title = firstUserMessage == null || firstUserMessage.isEmpty()
                ? (selfModel + " vs " + String.join(" / ", competitorModels))
                : (firstUserMessage.length() > 30 ? firstUserMessage.substring(0, 30) + "…" : firstUserMessage);
        s.setTitle(title);
        long now = System.currentTimeMillis();
        s.setAddTs(now);
        s.setLastActiveTs(now);
        sessionMapper.insert(s);
        return s;
    }

    /** 追加一条消息 */
    public DeepBenchmarkMessage appendMessage(Long sessionId, String role, String content, Map<String, Object> metadata) {
        DeepBenchmarkMessage m = new DeepBenchmarkMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        if (metadata != null && !metadata.isEmpty()) {
            try { m.setMetadata(objectMapper.writeValueAsString(metadata)); }
            catch (JsonProcessingException e) { log.warn("[Chat] metadata 序列化失败: {}", e.getMessage()); }
        }
        m.setAddTs(System.currentTimeMillis());
        messageMapper.insert(m);
        // 更新 session.last_active_ts
        sessionMapper.update(null, new LambdaUpdateWrapper<DeepBenchmarkSession>()
                .eq(DeepBenchmarkSession::getId, sessionId)
                .set(DeepBenchmarkSession::getLastActiveTs, m.getAddTs()));
        return m;
    }

    /** 列出某用户最近 N 个会话 */
    public List<DeepBenchmarkSession> listSessions(String userToken, int limit) {
        return sessionMapper.findByUserToken(userToken, limit);
    }

    public DeepBenchmarkSession getSession(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    public List<DeepBenchmarkMessage> getMessages(Long sessionId) {
        return messageMapper.findBySession(sessionId);
    }
}
