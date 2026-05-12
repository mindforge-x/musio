package com.musio.providers;

import java.util.Map;

public record SourceToolCall(
        String sourceId,
        String toolName,
        Map<String, Object> arguments
) {
    public SourceToolCall {
        sourceId = sourceId == null ? "" : sourceId.strip();
        toolName = toolName == null ? "" : toolName.strip();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
