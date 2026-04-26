package com.musio.model;

public record SongUrl(
        String songId,
        ProviderType provider,
        String url,
        Integer expiresInSeconds
) {
}
