package com.musio.agent;

import com.musio.events.SseEventPublisher;
import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import com.musio.model.ChatRunResponse;
import com.musio.model.PendingConfirmation;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentRunService {
    private final AgentRuntime agentRuntime;
    private final SseEventPublisher eventPublisher;
    private final Map<String, AgentEvent> initialEvents = new ConcurrentHashMap<>();

    public AgentRunService(AgentRuntime agentRuntime, SseEventPublisher eventPublisher) {
        this.agentRuntime = agentRuntime;
        this.eventPublisher = eventPublisher;
    }

    public ChatRunResponse startRun(ChatRequest request) {
        String runId = UUID.randomUUID().toString();
        initialEvents.put(runId, agentRuntime.start(runId, request));
        return new ChatRunResponse(runId, "created", "Agent run created.");
    }

    public SseEmitter connect(String runId) {
        SseEmitter emitter = eventPublisher.create(runId);
        AgentEvent event = initialEvents.remove(runId);
        if (event != null) {
            eventPublisher.publish(runId, event);
            eventPublisher.publish(runId, AgentEvent.of("done", Map.of("runId", runId)));
        }
        return emitter;
    }

    public ChatRunResponse confirm(String runId, PendingConfirmation confirmation) {
        return new ChatRunResponse(runId, "confirmed", "Confirmation accepted.");
    }

    public ChatRunResponse cancel(String runId) {
        return new ChatRunResponse(runId, "cancelled", "Agent run cancelled.");
    }
}
