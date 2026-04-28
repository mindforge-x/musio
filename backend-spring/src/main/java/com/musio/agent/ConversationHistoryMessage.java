package com.musio.agent;

import java.time.Instant;

public record ConversationHistoryMessage(
        String role,
        String content,
        Instant createdAt
) {
}
