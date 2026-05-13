package com.musio.memory;

import java.time.Instant;

public record PreferenceItem(
        String userId,
        String key,
        String polarity,
        String label,
        double confidence,
        String evidence,
        Instant updatedAt
) {
    public PreferenceItem {
        userId = userId == null || userId.isBlank() ? "local" : userId.strip();
        key = key == null ? "" : key.strip();
        polarity = polarity == null ? "positive" : polarity.strip().toLowerCase(java.util.Locale.ROOT);
        label = label == null ? "" : label.strip();
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        evidence = evidence == null ? "" : evidence.strip();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}
