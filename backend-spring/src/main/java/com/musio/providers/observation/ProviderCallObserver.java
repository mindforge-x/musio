package com.musio.providers.observation;

import com.musio.agent.AgentRunContext;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
import com.musio.model.ProviderType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class ProviderCallObserver {
    private final AgentEventBus eventBus;

    public ProviderCallObserver(AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public <T> T observe(
            ProviderType provider,
            String operation,
            Map<String, Object> inputPreview,
            Supplier<T> action,
            Function<T, Map<String, Object>> resultPreview
    ) {
        String runId = AgentRunContext.runId().orElse(null);
        if (runId == null) {
            return action.get();
        }

        String toolName = toolName(provider, operation);
        String spanId = UUID.randomUUID().toString();
        eventBus.publish(runId, AgentEvent.of("tool_start", Map.of(
                "runId", runId,
                "spanId", spanId,
                "layer", "provider_call",
                "visibility", "debug",
                "tool", toolName,
                "input", safePreview(inputPreview)
        )));

        long startedAt = System.nanoTime();
        try {
            T result = action.get();
            Map<String, Object> preview = safePreview(resultPreview.apply(result));
            eventBus.publish(runId, AgentEvent.of("tool_result", resultData(
                    runId,
                    spanId,
                    toolName,
                    "done",
                    summary(toolName, preview),
                    elapsedMs(startedAt),
                    preview
            )));
            return result;
        } catch (RuntimeException e) {
            eventBus.publish(runId, AgentEvent.of("tool_result", resultData(
                    runId,
                    spanId,
                    toolName,
                    "error",
                    toolName + " failed: " + errorMessage(e),
                    elapsedMs(startedAt),
                    Map.of("error", errorMessage(e))
            )));
            throw e;
        }
    }

    private Map<String, Object> resultData(
            String runId,
            String spanId,
            String toolName,
            String status,
            String summary,
            long durationMs,
            Map<String, Object> resultPreview
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", runId);
        data.put("spanId", spanId);
        data.put("layer", "provider_call");
        data.put("visibility", "debug");
        data.put("tool", toolName);
        data.put("status", status);
        data.put("summary", summary);
        data.put("durationMs", durationMs);
        data.put("result", resultPreview);
        return data;
    }

    private String toolName(ProviderType provider, String operation) {
        String providerName = provider == null ? "provider" : provider.name().toLowerCase(Locale.ROOT);
        return providerName + "." + operation;
    }

    private String summary(String toolName, Map<String, Object> preview) {
        Object count = preview.get("count");
        if (count != null) {
            return toolName + " returned " + count + " item(s)";
        }
        return toolName + " completed";
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String errorMessage(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private Map<String, Object> safePreview(Map<String, Object> preview) {
        if (preview == null || preview.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        preview.forEach((key, value) -> {
            if (key != null && value != null) {
                safe.put(key, value);
            }
        });
        return safe;
    }
}
