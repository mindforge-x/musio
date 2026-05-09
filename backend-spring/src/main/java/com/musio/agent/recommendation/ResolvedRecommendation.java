package com.musio.agent.recommendation;

import com.musio.model.Song;

public record ResolvedRecommendation(
        Song song,
        String reason,
        String matchedQuery,
        String slotId
) {
    public ResolvedRecommendation(Song song, String reason, String matchedQuery) {
        this(song, reason, matchedQuery, "");
    }

    public ResolvedRecommendation {
        slotId = slotId == null ? "" : slotId.strip();
    }
}
