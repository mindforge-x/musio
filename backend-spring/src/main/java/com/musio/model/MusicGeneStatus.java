package com.musio.model;

import java.time.Instant;

public record MusicGeneStatus(
        MusicGeneState state,
        ProviderType provider,
        String accountKey,
        String userId,
        String euin,
        Instant generatedAt,
        Instant profileGeneratedAt,
        Instant sourceGeneGeneratedAt,
        boolean profileSynced,
        String staleReason,
        String message
) {
    public static MusicGeneStatus unavailable(ProviderType provider, String message) {
        return new MusicGeneStatus(
                MusicGeneState.UNAVAILABLE,
                provider,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                message
        );
    }
}
