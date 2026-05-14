package com.musio.memory;

import java.time.Instant;
import java.util.List;

public record BehaviorSummary(
        String userId,
        String last24HoursSummary,
        String last7DaysSummary,
        List<String> negativeSignals,
        List<String> sceneSignals,
        Instant generatedAt
) {
    public BehaviorSummary {
        userId = userId == null || userId.isBlank() ? "local" : userId.strip();
        last24HoursSummary = last24HoursSummary == null ? "" : last24HoursSummary.strip();
        last7DaysSummary = last7DaysSummary == null ? "" : last7DaysSummary.strip();
        negativeSignals = clean(negativeSignals);
        sceneSignals = clean(sceneSignals);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }

    private static List<String> clean(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .limit(20)
                .toList();
    }
}
