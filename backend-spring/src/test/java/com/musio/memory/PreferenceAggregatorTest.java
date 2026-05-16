package com.musio.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreferenceAggregatorTest {
    @TempDir
    Path tempDir;

    @Test
    void discountsLlmSessionFeedbackLikeLegacySessionFeedback() {
        PreferenceStore preferenceStore = new PreferenceStore(new SQLiteMemoryDatabase(tempDir.resolve("memory.sqlite")));
        Instant now = Instant.parse("2026-05-13T10:00:00Z");
        preferenceStore.addCandidate(new PreferenceCandidate(
                "",
                "local",
                "negative",
                "too_noisy",
                "不想听太吵",
                0.2,
                "本轮临时说别太吵",
                "llm_session_feedback",
                now
        ));
        preferenceStore.addCandidate(new PreferenceCandidate(
                "",
                "local",
                "positive",
                "quiet_focus",
                "安静专注",
                0.2,
                "长期喜欢安静专注",
                "llm_explicit_feedback",
                now.plusSeconds(1)
        ));

        List<PreferenceItem> items = new PreferenceAggregator(preferenceStore).aggregate("local", now.plusSeconds(2));

        assertEquals(0.12, confidence(items, "negative:too_noisy"));
        assertEquals(0.2, confidence(items, "positive:quiet_focus"));
    }

    private double confidence(List<PreferenceItem> items, String key) {
        return items.stream()
                .filter(item -> key.equals(item.key()))
                .map(PreferenceItem::confidence)
                .findFirst()
                .orElse(0.0);
    }
}
