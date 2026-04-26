package com.musio.api;

import com.musio.config.MusioConfig;
import com.musio.config.MusioConfigService;
import com.musio.model.SystemStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final MusioConfigService configService;

    public SystemController(MusioConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/status")
    public SystemStatus status() {
        MusioConfig config = configService.config();
        return new SystemStatus(
                "ok",
                config.providers().qqmusic().sidecarBaseUrl(),
                config.configPath().toString(),
                config.ai().provider(),
                config.ai().model(),
                config.ai().apiKeyConfigured(),
                Instant.now()
        );
    }
}
