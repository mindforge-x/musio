package com.musio.ai;

import com.musio.config.MusioConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class SpringAiChatModelFactory {
    private static final String DEFAULT_API_KEY = "musio-local";

    public ChatClient chatClient(MusioConfig.Ai ai) {
        OpenAiEndpoint endpoint = endpoint(ai.baseUrl());
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(endpoint.baseUrl())
                .completionsPath(endpoint.completionsPath())
                .apiKey(ai.apiKeyConfigured() ? ai.apiKey() : DEFAULT_API_KEY)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(ai.model())
                .temperature(ai.temperature())
                .maxTokens(ai.maxTokens())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private OpenAiEndpoint endpoint(String configuredBaseUrl) {
        String value = trimTrailingSlash(configuredBaseUrl);
        URI uri = URI.create(value);
        String path = trimTrailingSlash(uri.getPath() == null ? "" : uri.getPath());

        if (path.endsWith("/chat/completions")) {
            return new OpenAiEndpoint(origin(uri), path);
        }
        if (path.endsWith("/v1")) {
            return new OpenAiEndpoint(origin(uri), path + "/chat/completions");
        }
        return new OpenAiEndpoint(value, "/v1/chat/completions");
    }

    private String origin(URI uri) {
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() >= 0) {
            builder.append(':').append(uri.getPort());
        }
        return builder.toString();
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record OpenAiEndpoint(String baseUrl, String completionsPath) {
    }
}
