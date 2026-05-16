package com.musio.memory;

public record LlmMusicInsight(
        String cacheType,
        String songId,
        String title,
        String artist,
        String content,
        String evidence
) {
    public LlmMusicInsight {
        cacheType = cacheType == null || cacheType.isBlank() ? "llmMusicInsight" : cacheType.strip();
        songId = safe(songId);
        title = safe(title);
        artist = safe(artist);
        content = safe(content);
        evidence = safe(evidence);
    }

    public boolean writable() {
        return !content.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
