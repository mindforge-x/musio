package com.musio.model;

import java.time.Instant;

public record AgentToolFailure(
        String toolName,
        String message,
        Instant occurredAt
) {
}
