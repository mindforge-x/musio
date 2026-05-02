package com.musio.agent;

import java.util.Optional;

public final class AgentRunContext {
    private static final ThreadLocal<String> CURRENT_RUN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CURRENT_TRACE_ENABLED = new ThreadLocal<>();

    private AgentRunContext() {
    }

    public static void setRunId(String runId) {
        CURRENT_RUN_ID.set(runId);
    }

    public static void setUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static void setTraceEnabled(boolean traceEnabled) {
        CURRENT_TRACE_ENABLED.set(traceEnabled);
    }

    public static Optional<String> runId() {
        return Optional.ofNullable(CURRENT_RUN_ID.get());
    }

    public static Optional<String> userId() {
        return Optional.ofNullable(CURRENT_USER_ID.get());
    }

    public static boolean traceEnabled() {
        return Boolean.TRUE.equals(CURRENT_TRACE_ENABLED.get());
    }

    public static void clear() {
        CURRENT_RUN_ID.remove();
        CURRENT_USER_ID.remove();
        CURRENT_TRACE_ENABLED.remove();
    }
}
