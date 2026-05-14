package com.musio.memory;

import com.musio.model.AgentTaskRecommendationSlot;
import com.musio.model.Song;

import java.util.List;

public record AgentTaskMemoryUpdate(
        List<Song> resultSongs,
        Song targetSong,
        String completedTaskType,
        List<String> observationSummaries,
        boolean replaceLoopEvidence,
        List<String> requiredOutcomes,
        List<AgentTaskRecommendationSlot> recommendationSlots,
        List<String> evidenceTools,
        List<String> writeIntentTools,
        boolean replaceStructuredEvidence
) {
    public AgentTaskMemoryUpdate {
        resultSongs = resultSongs == null ? List.of() : List.copyOf(resultSongs);
        completedTaskType = completedTaskType == null ? "" : completedTaskType.strip();
        observationSummaries = observationSummaries == null ? List.of() : List.copyOf(observationSummaries);
        requiredOutcomes = requiredOutcomes == null ? List.of() : List.copyOf(requiredOutcomes);
        recommendationSlots = recommendationSlots == null ? List.of() : List.copyOf(recommendationSlots);
        evidenceTools = evidenceTools == null ? List.of() : List.copyOf(evidenceTools);
        writeIntentTools = writeIntentTools == null ? List.of() : List.copyOf(writeIntentTools);
    }

    public static AgentTaskMemoryUpdate empty() {
        return new AgentTaskMemoryUpdate(List.of(), null, "", List.of(), false, List.of(), List.of(), List.of(), List.of(), false);
    }

    public boolean isEmpty() {
        return resultSongs.isEmpty() && !replaceLoopEvidence && !replaceStructuredEvidence;
    }
}
