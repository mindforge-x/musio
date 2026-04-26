package com.musio.model;

import java.time.Instant;

public record SystemStatus(
        String backend,
        String qqMusicSidecarBaseUrl,
        String configPath,
        String aiProvider,
        String aiModel,
        boolean aiApiKeyConfigured,
        Instant checkedAt
) {
}
