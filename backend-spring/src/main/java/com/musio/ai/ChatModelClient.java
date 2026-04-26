package com.musio.ai;

public interface ChatModelClient {
    ChatCompletionResult complete(ChatCompletionRequest request);
}
