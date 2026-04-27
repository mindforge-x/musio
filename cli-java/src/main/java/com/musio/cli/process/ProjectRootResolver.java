package com.musio.cli.process;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ProjectRootResolver {
    public Path resolve() {
        return fromCodeLocation()
                .or(() -> walkUp(Path.of("").toAbsolutePath()))
                .orElse(Path.of("").toAbsolutePath())
                .normalize();
    }

    private Optional<Path> fromCodeLocation() {
        try {
            Path location = Path.of(ProjectRootResolver.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .toAbsolutePath()
                    .normalize();
            if (Files.isRegularFile(location)) {
                return walkUp(location.getParent());
            }
            return walkUp(location);
        } catch (NullPointerException | URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Path> walkUp(Path start) {
        Path current = start;
        while (current != null) {
            if (isProjectRoot(current)) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private boolean isProjectRoot(Path path) {
        return Files.isDirectory(path.resolve("scripts"))
                && Files.isDirectory(path.resolve("backend-spring"))
                && Files.isDirectory(path.resolve("frontend"))
                && Files.isDirectory(path.resolve("providers").resolve("qqmusic-python-sidecar"));
    }
}
