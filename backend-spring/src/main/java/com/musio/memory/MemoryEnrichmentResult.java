package com.musio.memory;

import java.util.List;

public record MemoryEnrichmentResult(
        List<LlmPreferenceCandidate> preferenceCandidates,
        LlmConversationSummary conversationSummary,
        List<LlmMusicInsight> musicInsights,
        double confidence
) {
    public MemoryEnrichmentResult {
        preferenceCandidates = preferenceCandidates == null ? List.of() : List.copyOf(preferenceCandidates);
        conversationSummary = conversationSummary == null ? new LlmConversationSummary("", List.of()) : conversationSummary;
        musicInsights = musicInsights == null ? List.of() : List.copyOf(musicInsights);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public static MemoryEnrichmentResult empty() {
        return new MemoryEnrichmentResult(List.of(), new LlmConversationSummary("", List.of()), List.of(), 0.0);
    }

    public boolean isEmpty() {
        return preferenceCandidates.isEmpty() && conversationSummary.isEmpty() && musicInsights.isEmpty();
    }
}
