package com.musio.model;

public record LoginStartResult(
        String sessionId,
        ProviderType provider,
        LoginState state,
        String qrCodeDataUrl,
        String message
) {
}
