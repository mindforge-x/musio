package com.musio.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusioConfigServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadDynamicConfigOnlyRefreshesAiSettings() throws IOException {
        Path configPath = tempDir.resolve("config.toml");
        Path storageHome = tempDir.resolve("storage");
        Files.writeString(configPath, """
                [ai]
                provider = "openai-compatible"
                base_url = "http://127.0.0.1:11434/v1"
                api_key = "old-key"
                model = "old-model"
                temperature = 0.3
                max_tokens = 512

                [providers.qqmusic]
                sidecar_base_url = "http://127.0.0.1:18767"

                [storage]
                home = "%s"
                """.formatted(storageHome));

        MusioConfigService service = new MusioConfigService(new MockEnvironment()
                .withProperty("musio.config.path", configPath.toString()));

        Files.writeString(configPath, """
                [ai]
                provider = "openai-compatible"
                base_url = "http://127.0.0.1:8000/v1"
                api_key = "new-key"
                model = "new-model"
                temperature = 0.8
                max_tokens = 2048

                [providers.qqmusic]
                sidecar_base_url = "http://127.0.0.1:19999"

                [storage]
                home = "%s"
                """.formatted(tempDir.resolve("other-storage")));

        service.reloadDynamicConfig();

        MusioConfig config = service.config();
        assertEquals("http://127.0.0.1:8000/v1", config.ai().baseUrl());
        assertEquals("new-key", config.ai().apiKey());
        assertEquals("new-model", config.ai().model());
        assertEquals(0.8, config.ai().temperature());
        assertEquals(2048, config.ai().maxTokens());
        assertEquals(storageHome, config.storage().home());
        assertEquals("http://127.0.0.1:18767", config.providers().qqmusic().sidecarBaseUrl());
    }
}
