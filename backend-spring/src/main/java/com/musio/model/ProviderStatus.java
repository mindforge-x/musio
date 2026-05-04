package com.musio.model;

public record ProviderStatus(
        ProviderType provider,
        String displayName,
        boolean available,
        boolean authenticated,
        boolean credentialStored,
        String loginMethod,
        String message,
        String connectionState,
        String musicGeneState,
        MusicGeneStatus musicGeneStatus
) {
}
