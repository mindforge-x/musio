package com.musio.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MusicProfileMemory(
        ProviderType provider,
        String userId,
        Instant generatedAt,
        Instant sourceGeneGeneratedAt,
        String summary,
        List<String> strongPreferences,
        List<String> favoriteArtists,
        List<String> favoriteAlbums,
        List<String> likedSongExamples,
        List<String> recommendationHints,
        List<String> avoid,
        Map<String, Object> sourceStats
) {
}
