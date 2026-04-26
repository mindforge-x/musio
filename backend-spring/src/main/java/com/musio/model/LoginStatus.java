package com.musio.model;

public record LoginStatus(
        String sessionId,
        ProviderType provider,
        LoginState state,
        boolean credentialStored,
        String message
) {
}
