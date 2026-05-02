package com.musio.agent;

import java.util.List;
import java.util.Map;

record AgentToolPlan(
        List<AgentToolCall> toolCalls,
        double confidence
) {
    static AgentToolPlan empty() {
        return new AgentToolPlan(List.of(), 0.0);
    }

    boolean hasCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}

record AgentToolCall(
        String toolName,
        Map<String, Object> arguments
) {
}

record AgentToolExecution(
        String toolName,
        Map<String, Object> arguments,
        String resultJson
) {
}
