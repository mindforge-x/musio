package com.musio.model;

import java.time.Instant;
import java.util.List;

public record ChatHistoryMessage(
        String role,
        String content,
        Instant createdAt,
        List<Song> songs
) {
    public List<Song> songs() {
        return songs == null ? List.of() : List.copyOf(songs);
    }
}
