package com.musio.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentGoalNormalizerTest {
    @Test
    void derivesRecommendationOutcomeFromTaskTypeWhenPlannerOmittedOutcomes() {
        AgentTurnPlan turnPlan = new AgentTurnPlan(
                TurnDisposition.USE_TOOLS,
                "recommend",
                "new_task",
                "推荐适合专注的音乐",
                AgentTurnMemoryUse.none("新任务"),
                List.of(),
                List.of(),
                0.9,
                ""
        );

        assertEquals(List.of(AgentRequiredOutcome.RECOMMENDATION), AgentGoalNormalizer.requiredOutcomes(turnPlan, null));
    }

    @Test
    void addsTaskTypeOutcomeBeforePlannerDeclaredCompositeOutcomes() {
        AgentTurnPlan turnPlan = new AgentTurnPlan(
                TurnDisposition.USE_TOOLS,
                "recommend",
                "new_task",
                "推荐一首歌并获取热评",
                AgentTurnMemoryUse.none("新任务"),
                List.of(),
                List.of(AgentRequiredOutcome.COMMENTS),
                0.9,
                ""
        );

        assertEquals(
                List.of(AgentRequiredOutcome.RECOMMENDATION, AgentRequiredOutcome.COMMENTS),
                AgentGoalNormalizer.requiredOutcomes(turnPlan, null)
        );
    }

    @Test
    void derivesWriteOutcomeFromToolHint() {
        AgentTurnPlan turnPlan = new AgentTurnPlan(
                TurnDisposition.USE_TOOLS,
                "playlist",
                "refer_previous_song",
                "收藏第一首",
                AgentTurnMemoryUse.none("收藏"),
                List.of(new AgentToolCall("add_song_to_musio_playlist", Map.of("songIndex", 1))),
                List.of(),
                0.9,
                ""
        );

        assertEquals(
                List.of(AgentRequiredOutcome.PLAYLIST, AgentRequiredOutcome.LOCAL_PLAYLIST_WRITE),
                AgentGoalNormalizer.requiredOutcomes(turnPlan, null)
        );
    }

    @Test
    void derivesRecommendationSlotsFromRepeatedArtistCounts() {
        AgentTurnPlan turnPlan = new AgentTurnPlan(
                TurnDisposition.USE_TOOLS,
                "recommend",
                "new_task",
                "推荐一首许嵩的歌，再推荐一首许嵩的歌，一首后弦的歌，并获取热评、歌词、加入歌单",
                AgentTurnMemoryUse.none("新任务"),
                List.of(),
                List.of(AgentRequiredOutcome.RECOMMENDATION, AgentRequiredOutcome.COMMENTS, AgentRequiredOutcome.LYRICS, AgentRequiredOutcome.LOCAL_PLAYLIST_WRITE),
                0.9,
                ""
        );

        var slots = AgentGoalNormalizer.recommendationSlots(turnPlan, null, turnPlan.effectiveRequest());

        assertEquals(2, slots.size());
        assertEquals("许嵩", slots.get(0).target());
        assertEquals(2, slots.get(0).count());
        assertEquals("后弦", slots.get(1).target());
        assertEquals(1, slots.get(1).count());
    }
}
