package com.musio.agent.trace;

import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTracePublisherTest {
    private final AgentEventBus eventBus = new AgentEventBus();
    private final AgentTracePublisher publisher = new AgentTracePublisher(eventBus);

    @Test
    void shouldTraceMusicTasksButNotSmallTalk() {
        assertTrue(publisher.shouldTraceUserMessage("给我推荐 5 首适合深夜写代码听的歌"));
        assertTrue(publisher.shouldTraceUserMessage("找周杰伦的歌"));
        assertFalse(publisher.shouldTraceUserMessage("谢谢"));
        assertFalse(publisher.shouldTraceUserMessage("好的"));
    }

    @Test
    void detectsRecommendationRequestsSeparatelyFromSearchRequests() {
        assertTrue(publisher.shouldPlanRecommendation("给我推荐 5 首适合深夜写代码听的歌"));
        assertFalse(publisher.shouldPlanRecommendation("找周杰伦的歌"));
        assertFalse(publisher.shouldPlanRecommendation("谢谢"));
    }

    @Test
    void publishesToolTraceWithSafeDataWhitelist() {
        List<AgentEvent> events = new ArrayList<>();
        eventBus.subscribe("run-1", events::add);

        publisher.publishToolRunning("run-1", "search_songs", Map.of(
                "keyword", "周杰伦",
                "limit", 3,
                "rawToolResult", "secret"
        ));

        assertEquals(1, events.size());
        AgentEvent event = events.getFirst();
        assertEquals("trace_step", event.type());
        assertEquals("tool.search_songs", event.data().get("stepId"));
        assertEquals("running", event.data().get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> safeData = (Map<String, Object>) event.data().get("safeData");
        assertEquals("search_songs", safeData.get("tool"));
        assertEquals("周杰伦", safeData.get("keyword"));
        assertEquals(3, safeData.get("limit"));
        assertFalse(safeData.containsKey("rawToolResult"));
    }

    @Test
    void redactsForbiddenSummaryFragments() {
        List<AgentEvent> events = new ArrayList<>();
        eventBus.subscribe("run-2", events::add);

        publisher.publishToolError("run-2", "search_songs", "token=abc123 rawToolResult=secret");

        String summary = (String) events.getFirst().data().get("summary");
        assertFalse(summary.contains("abc123"));
        assertFalse(summary.contains("secret"));
    }
}
