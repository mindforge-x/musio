package com.musio.agent;

import java.util.Optional;

public final class AgentRunContext {
    private static final ThreadLocal<String> CURRENT_RUN_ID = new ThreadLocal<>();

    private AgentRunContext() {
    }

    public static void setRunId(String runId) {
        CURRENT_RUN_ID.set(runId);
    }

    public static Optional<String> runId() {
        return Optional.ofNullable(CURRENT_RUN_ID.get());
    }

    public static void clear() {
        CURRENT_RUN_ID.remove();
    }
}
