package com.musio.model;

import java.time.Instant;

public record SystemStatus(
        String backend,
        String qqMusicSidecarBaseUrl,
        Instant checkedAt
) {
}
