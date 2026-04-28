package com.musio.agent;

import com.musio.events.AgentEventBus;
import com.musio.events.SseEventPublisher;
import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import com.musio.model.ChatRunResponse;
import com.musio.model.PendingConfirmation;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class AgentRunService {
    private final AgentRuntime agentRuntime;
    private final SseEventPublisher eventPublisher;
    private final AgentEventBus eventBus;
    private final ConversationHistoryService conversationHistoryService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, ChatRequest> pendingRuns = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> runningRuns = new ConcurrentHashMap<>();

    public AgentRunService(
            AgentRuntime agentRuntime,
            SseEventPublisher eventPublisher,
            AgentEventBus eventBus,
            ConversationHistoryService conversationHistoryService
    ) {
        this.agentRuntime = agentRuntime;
        this.eventPublisher = eventPublisher;
        this.eventBus = eventBus;
        this.conversationHistoryService = conversationHistoryService;
    }

    public ChatRunResponse startRun(ChatRequest request) {
        String runId = UUID.randomUUID().toString();
        pendingRuns.put(runId, request);
        return new ChatRunResponse(runId, "created", "Agent run created.");
    }

    public SseEmitter connect(String runId) {
        SseEmitter emitter = eventPublisher.create(runId);
        ChatRequest request = pendingRuns.get(runId);
        if (request == null) {
            eventPublisher.publish(runId, AgentEvent.of("agent_error", Map.of(
                    "runId", runId,
                    "message", "Agent run not found or already started."
            )));
            return emitter;
        }

        eventBus.subscribe(runId, event -> {
            eventPublisher.publish(runId, event);
            if (isTerminal(event)) {
                eventBus.unsubscribe(runId);
                pendingRuns.remove(runId);
                runningRuns.remove(runId);
            }
        });

        runningRuns.computeIfAbsent(runId, id -> executorService.submit(() -> agentRuntime.start(id, request)));
        return emitter;
    }

    public ChatRunResponse confirm(String runId, PendingConfirmation confirmation) {
        return new ChatRunResponse(runId, "confirmed", "Confirmation accepted.");
    }

    public ChatRunResponse cancel(String runId) {
        pendingRuns.remove(runId);
        Future<?> task = runningRuns.remove(runId);
        if (task != null) {
            task.cancel(true);
        }
        eventBus.publish(runId, AgentEvent.of("done", Map.of("runId", runId, "state", "cancelled")));
        eventBus.unsubscribe(runId);
        return new ChatRunResponse(runId, "cancelled", "Agent run cancelled.");
    }

    public ChatRunResponse clearHistory(String userId) {
        String normalizedUserId = conversationHistoryService.normalizeUserId(userId);
        conversationHistoryService.clear(normalizedUserId);
        return new ChatRunResponse(normalizedUserId, "cleared", "Conversation history cleared.");
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private boolean isTerminal(AgentEvent event) {
        return "done".equals(event.type()) || "agent_error".equals(event.type());
    }
}
