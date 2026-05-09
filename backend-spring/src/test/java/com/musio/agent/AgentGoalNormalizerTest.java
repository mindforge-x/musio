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
}
