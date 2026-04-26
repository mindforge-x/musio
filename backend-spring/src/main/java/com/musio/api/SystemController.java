package com.musio.api;

import com.musio.model.SystemStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final String qqMusicSidecarBaseUrl;

    public SystemController(@Value("${musio.providers.qqmusic.sidecar-base-url}") String qqMusicSidecarBaseUrl) {
        this.qqMusicSidecarBaseUrl = qqMusicSidecarBaseUrl;
    }

    @GetMapping("/status")
    public SystemStatus status() {
        return new SystemStatus("ok", qqMusicSidecarBaseUrl, Instant.now());
    }
}
