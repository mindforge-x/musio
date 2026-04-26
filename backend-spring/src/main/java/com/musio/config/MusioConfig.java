package com.musio.config;

import java.nio.file.Path;

public record MusioConfig(
        Path configPath,
        Ai ai,
        Providers providers,
        Storage storage
) {
    public record Ai(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            double temperature,
            int maxTokens
    ) {
        public boolean apiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Providers(QQMusic qqmusic) {
    }

    public record QQMusic(String sidecarBaseUrl) {
    }

    public record Storage(Path home) {
    }
}
