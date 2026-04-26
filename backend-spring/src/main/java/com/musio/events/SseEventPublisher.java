package com.musio.events;

import com.musio.model.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SseEventPublisher.class);

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter create(String runId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(runId, emitter);
        emitter.onCompletion(() -> emitters.remove(runId));
        emitter.onTimeout(() -> emitters.remove(runId));
        emitter.onError(error -> emitters.remove(runId));
        publish(runId, AgentEvent.of("connected", Map.of("runId", runId)));
        return emitter;
    }

    public void publish(String runId, AgentEvent event) {
        SseEmitter emitter = emitters.get(runId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event));
            if ("done".equals(event.type()) || "agent_error".equals(event.type())) {
                emitter.complete();
                emitters.remove(runId);
            }
        } catch (IOException e) {
            log.warn("Failed to publish SSE event for run {}", runId, e);
            emitter.completeWithError(e);
            emitters.remove(runId);
        }
    }
}
