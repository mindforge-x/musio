package com.musio.memory;

import java.time.Instant;
import java.util.UUID;

public record PreferenceCandidate(
        String id,
        String userId,
        String polarity,
        String name,
        String label,
        double confidenceDelta,
        String evidence,
        String source,
        Instant createdAt
) {
    public PreferenceCandidate {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.strip();
        userId = userId == null || userId.isBlank() ? "local" : userId.strip();
        polarity = normalizePolarity(polarity);
        name = name == null ? "" : name.strip();
        label = label == null || label.isBlank() ? name : label.strip();
        confidenceDelta = Math.max(0.0, Math.min(0.5, confidenceDelta));
        evidence = evidence == null ? "" : evidence.strip();
        source = source == null ? "" : source.strip();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String normalizePolarity(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "positive", "negative" -> normalized;
            default -> "positive";
        };
    }
}
