package com.musio.agent;

import java.util.Map;

public record AgentLocalWriteIntent(
        String toolName,
        Map<String, Object> arguments
) {
    public AgentLocalWriteIntent {
        toolName = toolName == null ? "" : toolName.strip();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
