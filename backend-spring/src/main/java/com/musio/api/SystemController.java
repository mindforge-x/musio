package com.musio.api;

import com.musio.config.MusioConfig;
import com.musio.config.MusioConfigService;
import com.musio.config.RuntimeSourceContextService;
import com.musio.model.SourceContext;
import com.musio.model.SystemStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final MusioConfigService configService;
    private final RuntimeSourceContextService sourceContextService;

    public SystemController(MusioConfigService configService, RuntimeSourceContextService sourceContextService) {
        this.configService = configService;
        this.sourceContextService = sourceContextService;
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

    @GetMapping("/source-context")
    public SourceContext sourceContext() {
        return sourceContextService.context();
    }

    @PostMapping("/source-context")
    public SourceContext updateSourceContext(@RequestBody SourceContext sourceContext) {
        return sourceContextService.update(sourceContext);
    }
}
