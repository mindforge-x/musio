package com.musio.memory;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PreferenceAggregator {
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(90);

    private final PreferenceStore preferenceStore;

    public PreferenceAggregator(PreferenceStore preferenceStore) {
        this.preferenceStore = preferenceStore;
    }

    public List<PreferenceItem> aggregate(String userId) {
        return aggregate(userId, Instant.now());
    }

    public List<PreferenceItem> aggregate(String userId, Instant now) {
        if (preferenceStore == null) {
            return List.of();
        }
        String normalizedUserId = userId == null || userId.isBlank() ? "local" : userId.strip();
        Instant upper = now == null ? Instant.now() : now;
        List<PreferenceCandidate> candidates = preferenceStore.candidates(normalizedUserId, upper.minus(DEFAULT_WINDOW), 500);
        Map<String, AggregatedPreference> aggregated = new LinkedHashMap<>();
        for (PreferenceCandidate candidate : candidates) {
            if (candidate == null || candidate.name().isBlank()) {
                continue;
            }
            String key = key(candidate.polarity(), candidate.name());
            AggregatedPreference value = aggregated.computeIfAbsent(key, ignored -> new AggregatedPreference(candidate.polarity(), candidate.label()));
            value.confidence += confidenceWeight(candidate);
            if (value.evidence.isBlank() && !candidate.evidence().isBlank()) {
                value.evidence = candidate.evidence();
            }
        }
        List<PreferenceItem> items = aggregated.entrySet().stream()
                .map(entry -> new PreferenceItem(
                        normalizedUserId,
                        entry.getKey(),
                        entry.getValue().polarity,
                        entry.getValue().label,
                        Math.min(0.85, entry.getValue().confidence),
                        entry.getValue().evidence,
                        upper
                ))
                .filter(item -> item.confidence() >= 0.08)
                .toList();
        for (PreferenceItem item : items) {
            preferenceStore.upsertItem(item);
        }
        return items;
    }

    private double confidenceWeight(PreferenceCandidate candidate) {
        double weight = candidate.confidenceDelta();
        if ("session_feedback".equals(candidate.source())) {
            weight *= 0.6;
        }
        return Math.max(0.0, Math.min(0.35, weight));
    }

    private String key(String polarity, String name) {
        return safe(polarity).toLowerCase(Locale.ROOT) + ":" + safe(name).toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private static final class AggregatedPreference {
        private final String polarity;
        private final String label;
        private double confidence;
        private String evidence = "";

        private AggregatedPreference(String polarity, String label) {
            this.polarity = polarity;
            this.label = label;
        }
    }
}
