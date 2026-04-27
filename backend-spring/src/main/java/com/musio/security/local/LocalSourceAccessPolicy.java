package com.musio.security.local;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class LocalSourceAccessPolicy {
    public boolean isAllowed(Path candidate, List<AuthorizedMusicDirectory> authorizedDirectories) {
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        return authorizedDirectories.stream()
                .map(directory -> directory.root().toAbsolutePath().normalize())
                .anyMatch(normalizedCandidate::startsWith);
    }
}
