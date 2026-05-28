package com.nevinsight.intelligence.service;

import com.nevinsight.intelligence.config.DashScopeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PitchIterationService {

    private final QwenAiService qwenAiService;

    /**
     * 基于舆情内容迭代销售话术
     *
     * @param opinionContent 用户吐槽/舆情内容
     * @param knowledgeContext RAG检索的知识库上下文
     * @return 生成的新话术
     */
    public String iterateSalesPitch(String opinionContent, String knowledgeContext) {
        String prompt = String.format(
                "【背景】: 用户吐槽/舆情: %s \n" +
                "【知识库参考】: %s \n" +
                "【任务】: 请结合上述背景，针对性迭代销售话术。要求突出我司优势，对比竞品不足，语气专业、礼貌。",
                opinionContent, knowledgeContext
        );

        return qwenAiService.chatWithReportModel(
                "你是一位精通新能源汽车行业的架构师与营销专家。",
                prompt
        );
    }
}
