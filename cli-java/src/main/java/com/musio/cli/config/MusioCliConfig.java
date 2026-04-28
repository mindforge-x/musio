package com.musio.cli.config;

import java.net.URI;
import java.nio.file.Path;

public record MusioCliConfig(
        Path configPath,
        String serverHost,
        int serverPort,
        String webHost,
        int webPort,
        String qqMusicSidecarHost,
        int qqMusicSidecarPort
) {
    public String backendBaseUrl() {
        return "http://" + serverHost + ":" + serverPort;
    }

    public URI backendHealthUri() {
        return URI.create(backendBaseUrl() + "/actuator/health");
    }

    public URI systemStatusUri() {
        return URI.create(backendBaseUrl() + "/api/system/status");
    }

    public URI qqMusicLoginUri() {
        return URI.create(backendBaseUrl() + "/api/auth/qqmusic/qr");
    }

    public String webBaseUrl() {
        return "http://" + webHost + ":" + webPort;
    }

    public URI webUri() {
        return URI.create(webBaseUrl() + "/");
    }

    public String qqMusicSidecarBaseUrl() {
        return "http://" + qqMusicSidecarHost + ":" + qqMusicSidecarPort;
    }

    public URI qqMusicSidecarHealthUri() {
        return URI.create(qqMusicSidecarBaseUrl() + "/health");
    }

    public String corsAllowedOrigins() {
        return webBaseUrl() + ",http://localhost:" + webPort;
    }
}
