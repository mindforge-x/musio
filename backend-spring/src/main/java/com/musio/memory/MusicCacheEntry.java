package com.musio.memory;

import java.time.Instant;
import java.util.UUID;

public record MusicCacheEntry(
        String id,
        String userId,
        String cacheType,
        String songId,
        String title,
        String content,
        String evidence,
        Instant updatedAt
) {
    public MusicCacheEntry {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.strip();
        userId = userId == null || userId.isBlank() ? "local" : userId.strip();
        cacheType = cacheType == null ? "" : cacheType.strip();
        songId = songId == null ? "" : songId.strip();
        title = title == null ? "" : title.strip();
        content = content == null ? "" : content.strip();
        evidence = evidence == null ? "" : evidence.strip();
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}
