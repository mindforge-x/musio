package com.musio.playlists;

import java.time.Instant;
import java.util.List;

public record MusioPlaylist(
        String id,
        String name,
        String description,
        List<MusioPlaylistItem> items,
        Instant createdAt,
        Instant updatedAt
) {
}
