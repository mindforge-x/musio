package com.musio.agent;

import com.musio.agent.recommendation.RecommendationSlot;
import com.musio.agent.recommendation.RecommendationSlots;

import java.util.List;
import java.util.Map;

public record AgentGoal(
        String userMessage,
        String effectiveRequest,
        String taskType,
        String contextMode,
        boolean musicTask,
        boolean toolEvidenceExpected,
        boolean localWriteIntent,
        boolean accountWriteIntent,
        int requestedSongCount,
        List<RecommendationSlot> recommendationSlots,
        List<String> avoidSongTitles,
        List<AgentRequiredOutcome> requiredOutcomes,
        List<AgentLocalWriteIntent> localWriteIntents
) {
    public AgentGoal(
            String userMessage,
            String effectiveRequest,
            String taskType,
            String contextMode,
            boolean musicTask,
            boolean toolEvidenceExpected,
            boolean localWriteIntent,
            boolean accountWriteIntent,
            int requestedSongCount,
            List<String> avoidSongTitles,
            List<AgentRequiredOutcome> requiredOutcomes
    ) {
        this(userMessage, effectiveRequest, taskType, contextMode, musicTask, toolEvidenceExpected, localWriteIntent, accountWriteIntent, requestedSongCount, List.of(), avoidSongTitles, requiredOutcomes, List.of());
    }

    public AgentGoal(
            String userMessage,
            String effectiveRequest,
            String taskType,
            String contextMode,
            boolean musicTask,
            boolean toolEvidenceExpected,
            boolean localWriteIntent,
            boolean accountWriteIntent,
            int requestedSongCount,
            List<RecommendationSlot> recommendationSlots,
            List<String> avoidSongTitles,
            List<AgentRequiredOutcome> requiredOutcomes
    ) {
        this(userMessage, effectiveRequest, taskType, contextMode, musicTask, toolEvidenceExpected, localWriteIntent, accountWriteIntent, requestedSongCount, recommendationSlots, avoidSongTitles, requiredOutcomes, List.of());
    }

    public AgentGoal {
        userMessage = safe(userMessage);
        effectiveRequest = safe(effectiveRequest).isBlank() ? userMessage : safe(effectiveRequest);
        taskType = safe(taskType).isBlank() ? "unknown" : safe(taskType);
        contextMode = safe(contextMode).isBlank() ? "new_task" : safe(contextMode);
        recommendationSlots = RecommendationSlots.normalize(recommendationSlots);
        if (requestedSongCount <= 0 && !recommendationSlots.isEmpty()) {
            requestedSongCount = RecommendationSlots.totalCount(recommendationSlots);
        }
        requestedSongCount = Math.max(0, requestedSongCount);
        avoidSongTitles = avoidSongTitles == null ? List.of() : List.copyOf(avoidSongTitles);
        requiredOutcomes = requiredOutcomes == null ? List.of() : List.copyOf(requiredOutcomes);
        localWriteIntents = localWriteIntents == null ? List.of() : List.copyOf(localWriteIntents);
    }

    static AgentGoal from(String userMessage, AgentTurnPlan turnPlan, AgentTaskContext taskContext, int requestedSongCount) {
        return from(userMessage, turnPlan, taskContext, requestedSongCount, List.of());
    }

    static AgentGoal from(String userMessage, AgentTurnPlan turnPlan, AgentTaskContext taskContext, int requestedSongCount, List<RecommendationSlot> recommendationSlots) {
        String effectiveRequest = taskContext == null ? userMessage : taskContext.planningMessage();
        String taskType = taskContext == null ? "unknown" : taskContext.taskType();
        String contextMode = taskContext == null ? "new_task" : taskContext.contextMode();
        boolean toolEvidenceExpected = taskContext != null && taskContext.toolEvidenceExpected();
        List<AgentRequiredOutcome> requiredOutcomes = AgentGoalNormalizer.requiredOutcomes(turnPlan, taskContext, userMessage);
        if (requiredOutcomes.contains(AgentRequiredOutcome.RECOMMENDATION)) {
            taskType = "recommend";
            contextMode = "new_task";
        }
        return new AgentGoal(
                userMessage,
                effectiveRequest,
                taskType,
                contextMode,
                toolEvidenceExpected || (turnPlan != null && turnPlan.usesTools()) || !requiredOutcomes.isEmpty(),
                toolEvidenceExpected,
                (turnPlan != null && turnPlan.hasLocalWriteTools()) || requiredOutcomes.contains(AgentRequiredOutcome.LOCAL_PLAYLIST_WRITE),
                (turnPlan != null && turnPlan.hasAccountWriteTools()) || requiredOutcomes.contains(AgentRequiredOutcome.ACCOUNT_WRITE),
                requestedSongCount,
                recommendationSlots,
                taskContext == null ? List.of() : taskContext.avoidSongTitles(),
                requiredOutcomes,
                localWriteIntents(turnPlan)
        );
    }

    public int recommendationTotalCount() {
        return recommendationSlots.isEmpty() ? requestedSongCount : RecommendationSlots.totalCount(recommendationSlots);
    }

    public String plannerSummary() {
        return """
                goal: %s
                taskType: %s
                contextMode: %s
                musicTask: %s
                toolEvidenceExpected: %s
                localWriteIntent: %s
                accountWriteIntent: %s
                requestedSongCount: %s
                recommendationSlots: %s
                recommendationTotalCount: %s
                avoidSongTitles: %s
                requiredOutcomes: %s
                localWriteIntents: %s
                """.formatted(
                effectiveRequest,
                taskType,
                contextMode,
                musicTask,
                toolEvidenceExpected,
                localWriteIntent,
                accountWriteIntent,
                requestedSongCount <= 0 ? "unspecified" : requestedSongCount,
                RecommendationSlots.summary(recommendationSlots),
                recommendationTotalCount() <= 0 ? "unspecified" : recommendationTotalCount(),
                avoidSongTitles.isEmpty() ? "none" : String.join("、", avoidSongTitles),
                requiredOutcomes.isEmpty() ? "none" : requiredOutcomes,
                localWriteIntents.isEmpty() ? "none" : localWriteIntents.stream().map(AgentLocalWriteIntent::toolName).toList()
        ).strip();
    }

    private static List<AgentLocalWriteIntent> localWriteIntents(AgentTurnPlan turnPlan) {
        if (turnPlan == null) {
            return List.of();
        }
        return turnPlan.localWriteToolCalls().stream()
                .map(call -> new AgentLocalWriteIntent(call.toolName(), call.arguments() == null ? Map.of() : call.arguments()))
                .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
