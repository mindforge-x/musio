package com.musio.model;

import java.time.Instant;
import java.util.List;

public record AgentTaskMemory(
        String userId,
        String currentTask,
        String lastEffectiveRequest,
        String lastSearchKeyword,
        Integer lastSearchLimit,
        List<Song> lastResultSongs,
        List<String> lastResultSongTitles,
        List<String> avoidSongTitles,
        List<AgentToolFailure> lastToolFailures,
        Instant updatedAt
) {
    public static AgentTaskMemory empty(String userId) {
        return new AgentTaskMemory(
                userId,
                "",
                "",
                "",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Instant.EPOCH
        );
    }
}
