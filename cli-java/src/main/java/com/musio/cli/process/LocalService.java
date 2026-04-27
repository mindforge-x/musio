package com.musio.cli.process;

import java.net.URI;
import java.time.Duration;

public enum LocalService {
    QQMUSIC_SIDECAR(
            "musio-sidecar",
            "QQMusic sidecar",
            URI.create("http://127.0.0.1:18767/health"),
            "scripts/dev-sidecar.sh",
            "scripts\\win\\start-sidecar-windows.ps1",
            Duration.ofSeconds(30)
    ),
    BACKEND(
            "musio-backend",
            "Spring backend",
            URI.create("http://127.0.0.1:18765/actuator/health"),
            "scripts/dev-backend.sh",
            "scripts\\win\\start-backend-windows.ps1",
            Duration.ofSeconds(300)
    ),
    FRONTEND(
            "musio-frontend",
            "React frontend",
            URI.create("http://127.0.0.1:18766/"),
            "scripts/dev-frontend.sh",
            "scripts\\win\\start-frontend-windows.ps1",
            Duration.ofSeconds(90)
    );

    private final String processName;
    private final String displayName;
    private final URI healthUri;
    private final String unixScript;
    private final String windowsScript;
    private final Duration timeout;

    LocalService(
            String processName,
            String displayName,
            URI healthUri,
            String unixScript,
            String windowsScript,
            Duration timeout
    ) {
        this.processName = processName;
        this.displayName = displayName;
        this.healthUri = healthUri;
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

    public URI healthUri() {
        return healthUri;
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
