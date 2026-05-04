package com.musio.agent;

import com.musio.model.Song;

import java.util.List;
import java.util.Map;

record AgentTurnPlan(
        TurnDisposition disposition,
        String taskType,
        String contextMode,
        String effectiveRequest,
        AgentTurnMemoryUse memoryUse,
        List<AgentToolCall> toolCalls,
        double confidence,
        String fallbackReason
) {
    static AgentTurnPlan respondOnly(String effectiveRequest, double confidence, String fallbackReason) {
        return new AgentTurnPlan(
                TurnDisposition.RESPOND_ONLY,
                "chat",
                "new_task",
                safe(effectiveRequest),
                AgentTurnMemoryUse.none("本轮不需要读取短期任务记忆。"),
                List.of(),
                confidence,
                safe(fallbackReason)
        );
    }

    AgentToolPlan toToolPlan() {
        if (disposition != TurnDisposition.USE_TOOLS || toolCalls == null || toolCalls.isEmpty()) {
            return AgentToolPlan.empty();
        }
        return new AgentToolPlan(toolCalls, confidence);
    }

    AgentTaskContext toLegacyTaskContext(String originalMessage) {
        // 迁移期适配旧 composer / memory 结构；这里不能补自然语言场景特判。
        if (disposition != TurnDisposition.USE_TOOLS) {
            return AgentTaskContext.direct(originalMessage, confidence, "turn-planner");
        }
        SearchToolIntent searchIntent = firstSearchToolIntent();
        String targetSongId = firstSongId();
        boolean followUp = List.of("follow_up", "retry", "refer_previous_song", "correction").contains(safe(contextMode));
        return AgentTaskContext.agent(
                originalMessage,
                effectiveRequest.isBlank() ? originalMessage : effectiveRequest,
                searchIntent.keyword(),
                searchIntent.limit(),
                followUp,
                List.of(),
                confidence,
                "turn-planner",
                safe(taskType).isBlank() ? "unknown" : safe(taskType),
                targetSongId,
                "",
                safe(contextMode).isBlank() ? "new_task" : safe(contextMode),
                memoryUse == null ? AgentTaskMemoryAccess.none("Turn planner 未声明任务记忆使用。") : memoryUse.toLegacyAccess()
        );
    }

    boolean usesTools() {
        return disposition == TurnDisposition.USE_TOOLS && toolCalls != null && !toolCalls.isEmpty();
    }

    private SearchToolIntent firstSearchToolIntent() {
        if (toolCalls == null) {
            return new SearchToolIntent("", 0);
        }
        for (AgentToolCall call : toolCalls) {
            if (call != null && "search_songs".equals(call.toolName())) {
                Map<String, Object> arguments = call.arguments() == null ? Map.of() : call.arguments();
                return new SearchToolIntent(text(arguments, "keyword"), integer(arguments, "limit"));
            }
        }
        return new SearchToolIntent("", 0);
    }

    private String firstSongId() {
        if (toolCalls == null) {
            return "";
        }
        for (AgentToolCall call : toolCalls) {
            if (call == null || call.arguments() == null) {
                continue;
            }
            String songId = text(call.arguments(), "songId");
            if (!songId.isBlank()) {
                return songId;
            }
        }
        return "";
    }

    private static String text(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof String text ? text.strip() : "";
    }

    private static int integer(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private record SearchToolIntent(String keyword, int limit) {
    }
}

enum TurnDisposition {
    RESPOND_ONLY,
    USE_TOOLS,
    REQUEST_CONFIRMATION,
    UNSUPPORTED
}

record AgentTurnMemoryUse(
        boolean usesTaskMemory,
        List<String> usedFields,
        String reason
) {
    static AgentTurnMemoryUse none(String reason) {
        return new AgentTurnMemoryUse(false, List.of(), reason);
    }

    AgentTaskMemoryAccess toLegacyAccess() {
        if (!usesTaskMemory) {
            return AgentTaskMemoryAccess.none(reason);
        }
        List<String> fields = usedFields == null ? List.of() : usedFields;
        return new AgentTaskMemoryAccess(
                fields.contains("lastSearchKeyword"),
                fields.contains("lastResultSongs"),
                fields.contains("avoidSongTitles"),
                fields.contains("lastToolFailures"),
                reason == null || reason.isBlank() ? "Turn planner 声明需要读取短期任务记忆。" : reason
        );
    }

    String summary() {
        if (!usesTaskMemory) {
            return "none";
        }
        return String.join(",", usedFields == null ? List.of() : usedFields);
    }
}

record AgentTurnEvidence(
        List<AgentToolExecution> executions,
        List<Song> songs
) {
    static AgentTurnEvidence empty() {
        return new AgentTurnEvidence(List.of(), List.of());
    }

    AgentTurnEvidence {
        executions = executions == null ? List.of() : List.copyOf(executions);
        songs = songs == null ? List.of() : List.copyOf(songs);
    }
}
