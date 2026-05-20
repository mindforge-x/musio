package com.musio.providers;

import java.util.List;
import java.util.Set;

public record SourceManifest(
        String sourceId,
        String displayName,
        List<SourceCapability> capabilities
) {
    private static final Set<String> REQUIRED_P0_CAPABILITIES = Set.of(
            "get_source_status",
            "search_tracks",
            "get_track_detail",
            "get_track_playability",
            "resolve_playback"
    );

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

    public Set<String> missingRequiredP0Capabilities() {
        Set<String> enabledP0 = capabilities.stream()
                .filter(SourceCapability::enabled)
                .filter(SourceCapability::isP0)
                .map(SourceCapability::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return REQUIRED_P0_CAPABILITIES.stream()
                .filter(name -> !enabledP0.contains(name))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean p0Complete() {
        return missingRequiredP0Capabilities().isEmpty();
    }
}
