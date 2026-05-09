package com.musio.agent;

import java.util.List;

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
        List<String> avoidSongTitles,
        List<AgentRequiredOutcome> requiredOutcomes
) {
    public AgentGoal {
        userMessage = safe(userMessage);
        effectiveRequest = safe(effectiveRequest).isBlank() ? userMessage : safe(effectiveRequest);
        taskType = safe(taskType).isBlank() ? "unknown" : safe(taskType);
        contextMode = safe(contextMode).isBlank() ? "new_task" : safe(contextMode);
        requestedSongCount = Math.max(0, requestedSongCount);
        avoidSongTitles = avoidSongTitles == null ? List.of() : List.copyOf(avoidSongTitles);
        requiredOutcomes = requiredOutcomes == null ? List.of() : List.copyOf(requiredOutcomes);
    }

    static AgentGoal from(String userMessage, AgentTurnPlan turnPlan, AgentTaskContext taskContext, int requestedSongCount) {
        String effectiveRequest = taskContext == null ? userMessage : taskContext.planningMessage();
        String taskType = taskContext == null ? "unknown" : taskContext.taskType();
        String contextMode = taskContext == null ? "new_task" : taskContext.contextMode();
        boolean toolEvidenceExpected = taskContext != null && taskContext.toolEvidenceExpected();
        List<AgentRequiredOutcome> requiredOutcomes = AgentGoalNormalizer.requiredOutcomes(turnPlan, taskContext);
        return new AgentGoal(
                userMessage,
                effectiveRequest,
                taskType,
                contextMode,
                toolEvidenceExpected || (turnPlan != null && turnPlan.usesTools()),
                toolEvidenceExpected,
                (turnPlan != null && turnPlan.hasLocalWriteTools()) || requiredOutcomes.contains(AgentRequiredOutcome.LOCAL_PLAYLIST_WRITE),
                (turnPlan != null && turnPlan.hasAccountWriteTools()) || requiredOutcomes.contains(AgentRequiredOutcome.ACCOUNT_WRITE),
                requestedSongCount,
                taskContext == null ? List.of() : taskContext.avoidSongTitles(),
                requiredOutcomes
        );
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
                avoidSongTitles: %s
                requiredOutcomes: %s
                """.formatted(
                effectiveRequest,
                taskType,
                contextMode,
                musicTask,
                toolEvidenceExpected,
                localWriteIntent,
                accountWriteIntent,
                requestedSongCount <= 0 ? "unspecified" : requestedSongCount,
                avoidSongTitles.isEmpty() ? "none" : String.join("、", avoidSongTitles),
                requiredOutcomes.isEmpty() ? "none" : requiredOutcomes
        ).strip();
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
