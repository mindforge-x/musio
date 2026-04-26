package com.musio.model;

public record UserProfile(
        String id,
        ProviderType provider,
        String displayName,
        String avatarUrl
) {
}
