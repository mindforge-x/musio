package com.musio.model;

import java.util.List;

public record SongDetail(
        String id,
        ProviderType provider,
        String title,
        List<String> artists,
        String album,
        Integer durationSeconds,
        String artworkUrl,
        String sourceUrl
) {
}
