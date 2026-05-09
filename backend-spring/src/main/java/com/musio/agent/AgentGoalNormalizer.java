package com.musio.agent;

import com.musio.agent.recommendation.RecommendationSlot;
import com.musio.agent.recommendation.RecommendationSlots;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

final class AgentGoalNormalizer {
    private AgentGoalNormalizer() {
    }

    static List<AgentRequiredOutcome> requiredOutcomes(AgentTurnPlan turnPlan, AgentTaskContext taskContext) {
        LinkedHashSet<AgentRequiredOutcome> outcomes = new LinkedHashSet<>();
        if (turnPlan != null && turnPlan.disposition() == TurnDisposition.USE_TOOLS) {
            outcomeForTaskType(turnPlan.taskType()).ifPresent(outcomes::add);
            if (turnPlan.requiredOutcomes() != null) {
                outcomes.addAll(turnPlan.requiredOutcomes());
            }
            for (AgentToolCall call : turnPlan.toolCalls() == null ? List.<AgentToolCall>of() : turnPlan.toolCalls()) {
                outcomeForTool(call == null ? "" : call.toolName()).ifPresent(outcomes::add);
            }
        }
        if (taskContext != null && taskContext.agentTask()) {
            outcomeForTaskType(taskContext.taskType()).ifPresent(outcomes::add);
        }
        return List.copyOf(outcomes);
    }

    static List<RecommendationSlot> recommendationSlots(AgentTurnPlan turnPlan, AgentTaskContext taskContext, String userMessage) {
        if (turnPlan != null && turnPlan.recommendationSlots() != null && !turnPlan.recommendationSlots().isEmpty()) {
            return RecommendationSlots.normalize(turnPlan.recommendationSlots());
        }
        boolean recommendationTask = turnPlan != null && "recommend".equals(safe(turnPlan.taskType()));
        boolean recommendationOutcome = requiredOutcomes(turnPlan, taskContext).contains(AgentRequiredOutcome.RECOMMENDATION);
        if (!recommendationTask && !recommendationOutcome) {
            return List.of();
        }
        String source = turnPlan == null || safe(turnPlan.effectiveRequest()).isBlank()
                ? userMessage
                : turnPlan.effectiveRequest();
        List<RecommendationSlot> slots = RecommendationSlots.fromMessage(source);
        if (!slots.isEmpty()) {
            return slots;
        }
        return RecommendationSlots.fromMessage(userMessage);
    }

    private static Optional<AgentRequiredOutcome> outcomeForTaskType(String taskType) {
        return switch (safe(taskType)) {
            case "recommend" -> Optional.of(AgentRequiredOutcome.RECOMMENDATION);
            case "search" -> Optional.of(AgentRequiredOutcome.SEARCH);
            case "comments" -> Optional.of(AgentRequiredOutcome.COMMENTS);
            case "lyrics" -> Optional.of(AgentRequiredOutcome.LYRICS);
            case "detail" -> Optional.of(AgentRequiredOutcome.DETAIL);
            case "playlist" -> Optional.of(AgentRequiredOutcome.PLAYLIST);
            case "profile" -> Optional.of(AgentRequiredOutcome.PROFILE);
            case "playback" -> Optional.of(AgentRequiredOutcome.PLAYBACK);
            default -> Optional.empty();
        };
    }

    private static Optional<AgentRequiredOutcome> outcomeForTool(String toolName) {
        return switch (safe(toolName)) {
            case "search_songs" -> Optional.of(AgentRequiredOutcome.SEARCH);
            case "get_hot_comments" -> Optional.of(AgentRequiredOutcome.COMMENTS);
            case "get_lyrics" -> Optional.of(AgentRequiredOutcome.LYRICS);
            case "get_song_detail" -> Optional.of(AgentRequiredOutcome.DETAIL);
            case "get_user_playlists", "get_playlist_songs" -> Optional.of(AgentRequiredOutcome.PLAYLIST);
            case "get_user_music_profile" -> Optional.of(AgentRequiredOutcome.PROFILE);
            case "add_song_to_musio_playlist" -> Optional.of(AgentRequiredOutcome.LOCAL_PLAYLIST_WRITE);
            default -> Optional.empty();
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
