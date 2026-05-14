package com.musio.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationSummary(
        String id,
        String userId,
        String summary,
        List<String> keywords,
        Instant updatedAt
) {
    public ConversationSummary {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.strip();
        userId = userId == null || userId.isBlank() ? "local" : userId.strip();
        summary = summary == null ? "" : summary.strip();
        keywords = keywords == null ? List.of() : keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .limit(20)
                .toList();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}
