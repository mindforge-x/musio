package com.musio.ai;

public record ChatCompletionResult(
        String text,
        String provider,
        String model
) {
}
