package com.nevinsight.intelligence.agent;

public interface ReportAgent<I, O> {

    String name();

    O execute(I input) throws AgentExecutionException;

    O fallback(I input, Exception cause);
}
