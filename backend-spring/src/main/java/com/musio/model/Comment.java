package com.musio.model;

import java.time.Instant;

public record Comment(
        String id,
        String songId,
        ProviderType provider,
        String authorName,
        String text,
        Integer likedCount,
        Instant createdAt
) {
}
