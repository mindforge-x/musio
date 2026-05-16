package com.musio.memory;

import java.util.Locale;

public record LlmPreferenceCandidate(
        String polarity,
        String name,
        String label,
        double confidenceDelta,
        String scope,
        String evidence
) {
    public LlmPreferenceCandidate {
        polarity = normalizePolarity(polarity);
        name = safe(name);
        label = safe(label);
        confidenceDelta = Math.max(0.05, Math.min(0.3, confidenceDelta));
        scope = normalizeScope(scope);
        evidence = safe(evidence);
    }

    public boolean writable() {
        return !name.isBlank() && !"ignore".equals(scope);
    }

    private static String normalizePolarity(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "negative" -> "negative";
            default -> "positive";
        };
    }

    private static String normalizeScope(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "session", "long_term", "ignore" -> normalized;
            default -> "ignore";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
