package com.musio.ai;

import com.musio.config.MusioConfig;

import java.util.List;

public record ChatCompletionRequest(
        List<ChatMessage> messages,
        MusioConfig.Ai options
) {
}
