package com.musio.agent;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAnswerStreamGuardTest {
    @Test
    void suppressesRawToolCallProtocolFromFirstChunk() {
        AgentAnswerStreamGuard guard = new AgentAnswerStreamGuard();

        assertEquals(Optional.empty(), guard.accept("<tool_call>\n<function=search_songs>"));
        assertEquals(Optional.empty(), guard.accept("<parameter=keyword>适合深夜听的歌</parameter>"));
        Optional<String> fallback = guard.finish("请重试");

        assertTrue(guard.rawToolProtocolSuppressed());
        assertEquals(Optional.of("请重试"), fallback);
        assertEquals("请重试", guard.visibleAnswer());
    }

    @Test
    void suppressesRawToolCallProtocolSplitAcrossChunks() {
        AgentAnswerStreamGuard guard = new AgentAnswerStreamGuard();

        assertEquals(Optional.empty(), guard.accept("<"));
        assertEquals(Optional.empty(), guard.accept("tool_call>"));

        assertTrue(guard.rawToolProtocolSuppressed());
    }

    @Test
    void publishesNaturalLanguageNormally() {
        AgentAnswerStreamGuard guard = new AgentAnswerStreamGuard();

        assertEquals(Optional.of("我给你推荐几首适合深夜听的歌。"), guard.accept("我给你推荐几首适合深夜听的歌。"));
        assertEquals(Optional.of("\n1. 《晴天》"), guard.accept("\n1. 《晴天》"));
        assertEquals(Optional.empty(), guard.finish("请重试"));

        assertFalse(guard.rawToolProtocolSuppressed());
        assertEquals("我给你推荐几首适合深夜听的歌。\n1. 《晴天》", guard.visibleAnswer());
    }
}
