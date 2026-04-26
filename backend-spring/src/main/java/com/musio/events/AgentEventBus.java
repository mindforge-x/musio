package com.musio.events;

import com.musio.model.AgentEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class AgentEventBus {
    private final Map<String, Consumer<AgentEvent>> listeners = new ConcurrentHashMap<>();

    public void subscribe(String runId, Consumer<AgentEvent> listener) {
        listeners.put(runId, listener);
    }

    public void unsubscribe(String runId) {
        listeners.remove(runId);
    }

    public void publish(String runId, AgentEvent event) {
        Consumer<AgentEvent> listener = listeners.get(runId);
        if (listener != null) {
            listener.accept(event);
        }
    }
}
