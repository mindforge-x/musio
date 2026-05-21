package com.musio.agent.capability;

import com.musio.agent.loop.AgentLoopState;

import java.util.Map;

final class MusicReadCapabilityValidator {
    private MusicReadCapabilityValidator() {
    }

    static AgentCapabilityValidationResult validate(
            AgentLoopState state,
            String capabilityName,
            Map<String, Object> arguments,
            boolean supported
    ) {
        if (!supported) {
            return AgentCapabilityValidationResult.rejected("unknown_tool");
        }
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        AgentCapabilityValidationResult requiredArguments = AgentCapabilityArgumentRules.validateReadRequiredArguments(capabilityName, safeArguments);
        if (!requiredArguments.valid()) {
            return requiredArguments;
        }
        if ("search_songs".equals(capabilityName)
                && AgentCapabilityStateFacts.searchedKeywords(state).contains(AgentCapabilityStateFacts.normalizedKeyword(AgentCapabilityStateFacts.text(safeArguments, "keyword")))) {
            return AgentCapabilityValidationResult.rejected("search_keyword_already_observed");
        }
        if (requiresSongId(capabilityName)
                && !AgentCapabilityStateFacts.knownSongIds(state).contains(AgentCapabilityStateFacts.text(safeArguments, "songId"))) {
            return AgentCapabilityValidationResult.rejected("song_id_not_observed");
        }
        if (supportsBatchSongIds(capabilityName)) {
            java.util.List<String> songIds = AgentCapabilityStateFacts.songIds(safeArguments);
            if (!songIds.isEmpty() && !AgentCapabilityStateFacts.knownSongIds(state).containsAll(songIds)) {
                return AgentCapabilityValidationResult.rejected("song_id_not_observed");
            }
            if (!songIds.isEmpty() && AgentCapabilityStateFacts.successfulReadSongIds(state, capabilityName).containsAll(songIds)) {
                return AgentCapabilityValidationResult.rejected("tool_result_already_observed");
            }
        }
        if (requiresObservedPlaylistId(capabilityName)
                && !AgentCapabilityStateFacts.knownPlaylistIds(state).contains(AgentCapabilityStateFacts.text(safeArguments, "playlistId"))) {
            return AgentCapabilityValidationResult.rejected("playlist_id_not_observed");
        }
        if (requiresObservedAlbumId(capabilityName)
                && !knownIdMatches(AgentCapabilityStateFacts.knownAlbumIds(state), AgentCapabilityStateFacts.text(safeArguments, "albumId"))
                && !userMessageContainsId(state, AgentCapabilityStateFacts.text(safeArguments, "albumId"))) {
            return AgentCapabilityValidationResult.rejected("album_id_not_observed");
        }
        if (requiresObservedChartId(capabilityName)
                && !looksLikeChartId(AgentCapabilityStateFacts.text(safeArguments, "chartId"))
                && !knownIdMatches(AgentCapabilityStateFacts.knownChartIds(state), AgentCapabilityStateFacts.text(safeArguments, "chartId"))) {
            return AgentCapabilityValidationResult.rejected("chart_id_not_observed");
        }
        return AgentCapabilityValidationResult.accepted();
    }

    private static boolean requiresSongId(String capabilityName) {
        return "get_song_detail".equals(capabilityName);
    }

    private static boolean supportsBatchSongIds(String capabilityName) {
        return "get_lyrics".equals(capabilityName) || "get_hot_comments".equals(capabilityName);
    }

    private static boolean requiresObservedPlaylistId(String capabilityName) {
        return "get_playlist_songs".equals(capabilityName)
                || "get_playlist_tracks".equals(capabilityName)
                || "get_playlist_detail".equals(capabilityName);
    }

    private static boolean requiresObservedAlbumId(String capabilityName) {
        return "get_album_detail".equals(capabilityName) || "get_album_tracks".equals(capabilityName);
    }

    private static boolean requiresObservedChartId(String capabilityName) {
        return "get_chart_detail".equals(capabilityName);
    }

    private static boolean looksLikeChartId(String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            return false;
        }
        String value = requestedId.strip();
        if (value.matches("\\d+")) {
            return true;
        }
        String prefix = "qqmusic:chart:";
        return value.startsWith(prefix) && value.substring(prefix.length()).matches("\\d+");
    }

    private static boolean knownIdMatches(java.util.Set<String> knownIds, String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            return false;
        }
        String requested = requestedId.strip();
        if (knownIds.contains(requested)) {
            return true;
        }
        for (String knownId : knownIds) {
            if (knownId != null && !knownId.isBlank() && knownId.strip().endsWith(":" + requested)) {
                return true;
            }
        }
        return false;
    }

    private static boolean userMessageContainsId(AgentLoopState state, String requestedId) {
        if (state == null || requestedId == null || requestedId.isBlank()) {
            return false;
        }
        String userMessage = state.userMessage() == null ? "" : state.userMessage();
        return !userMessage.isBlank() && userMessage.contains(requestedId.strip());
    }
}
