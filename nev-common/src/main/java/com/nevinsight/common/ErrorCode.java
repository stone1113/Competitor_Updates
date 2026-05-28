package com.nevinsight.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    UNSUPPORTED_PLATFORM(400, "不支持的平台"),
    CRAWLER_API_ERROR(502, "爬虫服务调用失败"),
    LLM_API_ERROR(503, "大模型服务调用失败"),
    LLM_RATE_LIMITED(429, "大模型服务限流"),
    MILVUS_ERROR(503, "向量数据库服务异常"),
    KAFKA_ERROR(503, "消息队列服务异常"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
