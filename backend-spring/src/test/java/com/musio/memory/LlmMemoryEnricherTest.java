package com.musio.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmMemoryEnricherTest {
    private final LlmMemoryEnricher enricher = new LlmMemoryEnricher(null, null, new ObjectMapper());

    @Test
    void parsesStructuredEnrichmentJson() {
        Optional<MemoryEnrichmentResult> result = enricher.parseResult("""
                ```json
                {
                  "preferenceCandidates": [
                    {"polarity":"negative","name":"too_noisy","label":"不想听太吵","confidenceDelta":0.9,"scope":"long_term","evidence":"别太吵"},
                    {"polarity":"positive","name":"temporary_mood","label":"临时心情","confidenceDelta":0.01,"scope":"ignore","evidence":"你好"}
                  ],
                  "conversationSummary": {
                    "summary": "用户明确表达不想听太吵的歌。",
                    "keywords": ["安静", "偏好", "安静"]
                  },
                  "musicInsights": [
                    {"songId":"qqmusic:1","title":"安静","artist":"周杰伦","content":"评论认为这首歌适合慢下来。","evidence":"comments"}
                  ],
                  "confidence": 0.8
                }
                ```
                """);

        assertTrue(result.isPresent());
        MemoryEnrichmentResult value = result.get();
        assertEquals(2, value.preferenceCandidates().size());
        assertEquals("negative", value.preferenceCandidates().getFirst().polarity());
        assertEquals(0.3, value.preferenceCandidates().getFirst().confidenceDelta());
        assertFalse(value.preferenceCandidates().get(1).writable());
        assertEquals("用户明确表达不想听太吵的歌。", value.conversationSummary().summary());
        assertEquals(2, value.conversationSummary().keywords().size());
        assertEquals("llmMusicInsight", value.musicInsights().getFirst().cacheType());
    }

    @Test
    void rejectsLowConfidenceAndInvalidJson() {
        assertTrue(enricher.parseResult("""
                {"preferenceCandidates":[{"name":"too_noisy"}],"confidence":0.2}
                """).isEmpty());
        assertTrue(enricher.parseResult("not json").isEmpty());
        assertTrue(enricher.parseResult("").isEmpty());
    }
}
