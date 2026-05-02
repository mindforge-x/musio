package com.musio.agent.recommendation;

public record RecommendationCandidate(
        String title,
        String artist,
        String reason
) {
}
