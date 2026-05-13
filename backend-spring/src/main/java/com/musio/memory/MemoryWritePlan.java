package com.musio.memory;

import java.util.List;

public record MemoryWritePlan(
        List<BehaviorEvent> behaviorEvents,
        List<PreferenceCandidate> preferenceCandidates,
        List<MusicCacheEntry> musicCacheEntries,
        List<ConversationSummary> conversationSummaries
) {
    public MemoryWritePlan {
        behaviorEvents = behaviorEvents == null ? List.of() : List.copyOf(behaviorEvents);
        preferenceCandidates = preferenceCandidates == null ? List.of() : List.copyOf(preferenceCandidates);
        musicCacheEntries = musicCacheEntries == null ? List.of() : List.copyOf(musicCacheEntries);
        conversationSummaries = conversationSummaries == null ? List.of() : List.copyOf(conversationSummaries);
    }

    public static MemoryWritePlan empty() {
        return new MemoryWritePlan(List.of(), List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return behaviorEvents.isEmpty()
                && preferenceCandidates.isEmpty()
                && musicCacheEntries.isEmpty()
                && conversationSummaries.isEmpty();
    }
}
