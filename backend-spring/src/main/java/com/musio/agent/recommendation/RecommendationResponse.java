package com.musio.agent.recommendation;

import com.musio.model.Song;

import java.util.List;

public record RecommendationResponse(
        String answerText,
        List<Song> songs,
        RecommendationResult result,
        List<RecommendationSlot> slots
) {
    public RecommendationResponse(String answerText, List<Song> songs, RecommendationResult result) {
        this(answerText, songs, result, List.of());
    }

    public RecommendationResponse {
        slots = RecommendationSlots.normalize(slots);
    }
}
