package com.musio.agent.recommendation;

import java.util.List;

public record RecommendationDraft(
        List<RecommendationCandidate> candidates,
        double confidence,
        String source
) {
}
