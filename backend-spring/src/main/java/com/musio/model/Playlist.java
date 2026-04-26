package com.musio.model;

public record Playlist(
        String id,
        ProviderType provider,
        String name,
        Integer songCount,
        String artworkUrl
) {
}
