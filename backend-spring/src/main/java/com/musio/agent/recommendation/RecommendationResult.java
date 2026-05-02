package com.musio.agent.recommendation;

import java.util.List;

public record RecommendationResult(
        List<ResolvedRecommendation> resolved,
        List<RecommendationCandidate> unresolved,
        String summary
) {
}
