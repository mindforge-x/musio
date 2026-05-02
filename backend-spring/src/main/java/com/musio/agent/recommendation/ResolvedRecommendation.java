package com.musio.agent.recommendation;

import com.musio.model.Song;

public record ResolvedRecommendation(
        Song song,
        String reason,
        String matchedQuery
) {
}
