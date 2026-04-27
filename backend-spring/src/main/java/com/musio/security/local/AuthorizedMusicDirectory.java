package com.musio.security.local;

import java.nio.file.Path;
import java.time.Instant;

public record AuthorizedMusicDirectory(
        String id,
        Path root,
        Instant authorizedAt
) {
}
