package com.musio.providers;

import java.util.List;

public record SourceManifest(
        String sourceId,
        String displayName,
        List<SourceCapability> capabilities
) {
    public SourceManifest {
        sourceId = sourceId == null ? "" : sourceId.strip();
        displayName = displayName == null ? "" : displayName.strip();
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public List<SourceCapability> enabledCapabilities() {
        return capabilities.stream()
                .filter(SourceCapability::enabled)
                .toList();
    }
}
