package com.musio.events;

import com.musio.model.AgentEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class AgentEventBus {
    // 轻量级内存事件总线：只按 runId 转发当前运行中的 Agent 事件，不做事件持久化。
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
