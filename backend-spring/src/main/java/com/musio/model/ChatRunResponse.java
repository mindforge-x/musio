package com.musio.model;

public record ChatRunResponse(
        String runId,
        String state,
        String message
) {
}
