package com.musio.memory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BehaviorEvent(
        String id,
        String userId,
        String type,
        String source,
        String songId,
        String songTitle,
        List<String> artists,
        String evidence,
        Map<String, Object> payload,
        double confidence,
        Instant createdAt
) {
    public BehaviorEvent {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.strip();
        userId = userId == null || userId.isBlank() ? "local" : userId.strip();
        type = type == null ? "" : type.strip();
        source = source == null ? "" : source.strip();
        songId = songId == null ? "" : songId.strip();
        songTitle = songTitle == null ? "" : songTitle.strip();
        artists = artists == null ? List.of() : artists.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
        evidence = evidence == null ? "" : evidence.strip();
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
