package com.musio.agent.recommendation;

public record RecommendationCandidate(
        String title,
        String artist,
        String reason,
        String slotId
) {
    public RecommendationCandidate(String title, String artist, String reason) {
        this(title, artist, reason, "");
    }

    public RecommendationCandidate {
        slotId = slotId == null ? "" : slotId.strip();
    }
}
