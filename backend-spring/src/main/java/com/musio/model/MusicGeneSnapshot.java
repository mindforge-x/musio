package com.musio.model;

import java.time.Instant;
import java.util.Map;

public record MusicGeneSnapshot(
        ProviderType provider,
        String userId,
        String euin,
        Instant generatedAt,
        Map<String, Object> data
) {
}
