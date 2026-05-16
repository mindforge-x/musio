package com.musio.memory;

import java.util.List;

public record LlmConversationSummary(
        String summary,
        List<String> keywords
) {
    public LlmConversationSummary {
        summary = summary == null ? "" : summary.strip();
        keywords = keywords == null ? List.of() : keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .distinct()
                .limit(20)
                .toList();
    }

    public boolean isEmpty() {
        return summary.isBlank() && keywords.isEmpty();
    }
}
