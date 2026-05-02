package com.musio.agent.recommendation;

import com.musio.model.Song;

import java.util.List;

public record RecommendationResponse(
        String answerText,
        List<Song> songs,
        RecommendationResult result
) {
}
