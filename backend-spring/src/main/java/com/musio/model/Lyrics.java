package com.musio.model;

public record Lyrics(
        String songId,
        ProviderType provider,
        String plainText,
        String syncedText
) {
}
