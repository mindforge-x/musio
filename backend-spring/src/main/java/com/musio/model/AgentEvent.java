package com.musio.model;

import java.time.Instant;
import java.util.Map;

public record AgentEvent(
        String type,
        Map<String, Object> data,
        Instant createdAt
) {
    public static AgentEvent of(String type, Map<String, Object> data) {
        return new AgentEvent(type, data, Instant.now());
    }
}
