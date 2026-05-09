package com.musio.agent.recommendation;

public record RecommendationSlot(
        String slotId,
        String targetType,
        String target,
        int count
) {
    public RecommendationSlot {
        targetType = safe(targetType).isBlank() ? "artist" : safe(targetType);
        target = safe(target);
        count = Math.max(1, Math.min(10, count));
        slotId = safe(slotId).isBlank() ? RecommendationSlots.slotId(targetType, target) : safe(slotId);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
