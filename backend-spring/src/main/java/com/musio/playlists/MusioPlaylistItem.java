package com.musio.playlists;

import com.musio.model.ProviderType;

import java.time.Instant;
import java.util.List;

public record MusioPlaylistItem(
        String id,
        String playlistId,
        ProviderType provider,
        String providerTrackId,
        String title,
        List<String> artists,
        String album,
        Integer durationSeconds,
        String artworkUrl,
        String sourceUrl,
        int sortOrder,
        Instant createdAt
) {
}
