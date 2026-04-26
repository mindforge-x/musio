package com.musio.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String userId,
        @NotBlank String message
) {
}
