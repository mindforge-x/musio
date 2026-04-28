package com.musio.cli.process;

import com.musio.cli.config.MusioCliConfig;

import java.net.URI;
import java.time.Duration;

public enum LocalService {
    QQMUSIC_SIDECAR(
            "musio-sidecar",
            "QQMusic sidecar",
            "scripts/dev-sidecar.sh",
            "scripts\\win\\start-sidecar-windows.ps1",
            Duration.ofSeconds(30)
    ),
    BACKEND(
            "musio-backend",
            "Spring backend",
            "scripts/dev-backend.sh",
            "scripts\\win\\start-backend-windows.ps1",
            Duration.ofSeconds(300)
    ),
    FRONTEND(
            "musio-frontend",
            "React frontend",
            "scripts/dev-frontend.sh",
            "scripts\\win\\start-frontend-windows.ps1",
            Duration.ofSeconds(90)
    );

    private final String processName;
    private final String displayName;
    private final String unixScript;
    private final String windowsScript;
    private final Duration timeout;

    LocalService(
            String processName,
            String displayName,
            String unixScript,
            String windowsScript,
            Duration timeout
    ) {
        this.processName = processName;
        this.displayName = displayName;
        this.unixScript = unixScript;
        this.windowsScript = windowsScript;
        this.timeout = timeout;
    }

    public String processName() {
        return processName;
    }

    public String displayName() {
        return displayName;
    }

    public URI healthUri(MusioCliConfig config) {
        return switch (this) {
            case QQMUSIC_SIDECAR -> config.qqMusicSidecarHealthUri();
            case BACKEND -> config.backendHealthUri();
            case FRONTEND -> config.webUri();
        };
    }

    public String portConfigKey() {
        return switch (this) {
            case QQMUSIC_SIDECAR -> "providers.qqmusic.sidecar_port";
            case BACKEND -> "server.port";
            case FRONTEND -> "web.port";
        };
    }

    public String unixScript() {
        return unixScript;
    }

    public String windowsScript() {
        return windowsScript;
    }

    public Duration timeout() {
        return timeout;
    }
}
