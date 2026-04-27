package com.musio.model;

public record ProviderStatus(
        ProviderType provider,
        String displayName,
        boolean available,
        boolean authenticated,
        String loginMethod,
        String message
) {
}
