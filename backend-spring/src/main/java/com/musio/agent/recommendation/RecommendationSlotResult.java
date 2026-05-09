package com.musio.agent.recommendation;

public record RecommendationSlotResult(
        String slotId,
        int requested,
        int resolved
) {
    public RecommendationSlotResult {
        slotId = slotId == null ? "" : slotId.strip();
        requested = Math.max(0, requested);
        resolved = Math.max(0, resolved);
    }

    public boolean satisfied() {
        return resolved >= requested;
    }
}
