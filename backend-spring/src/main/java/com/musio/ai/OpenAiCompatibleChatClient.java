package com.musio.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.config.MusioConfig;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleChatClient implements ChatModelClient {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public ChatCompletionResult complete(ChatCompletionRequest request) {
        MusioConfig.Ai ai = request.options();
        HttpUrl endpoint = chatCompletionsEndpoint(ai.baseUrl());

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", ai.model());
        payload.put("temperature", ai.temperature());
        payload.put("max_tokens", ai.maxTokens());
        payload.put("stream", false);
        payload.put("messages", request.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());

        Request.Builder builder = new Request.Builder()
                .url(endpoint)
                .post(jsonBody(payload))
                .addHeader("Content-Type", "application/json");

        if (ai.apiKeyConfigured()) {
            builder.addHeader("Authorization", "Bearer " + ai.apiKey());
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            String responseText = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                throw new ChatModelException("Model request failed with HTTP " + response.code() + ": " + responseText);
            }
            return new ChatCompletionResult(extractText(responseText), ai.provider(), ai.model());
        } catch (IOException e) {
            throw new ChatModelException("Model request failed: " + e.getMessage(), e);
        }
    }

    private RequestBody jsonBody(Map<String, Object> payload) {
        try {
            return RequestBody.create(
                    objectMapper.writeValueAsBytes(payload),
                    MediaType.get("application/json; charset=utf-8")
            );
        } catch (IOException e) {
            throw new ChatModelException("Failed to serialize model request.", e);
        }
    }

    private String extractText(String responseText) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseText, new TypeReference<>() {
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new ChatModelException("Model response did not include choices.");
            }

            Object messageObject = choices.get(0).get("message");
            if (messageObject instanceof Map<?, ?> message) {
                Object content = message.get("content");
                return content == null ? "" : String.valueOf(content);
            }

            Object text = choices.get(0).get("text");
            return text == null ? "" : String.valueOf(text);
        } catch (IOException e) {
            throw new ChatModelException("Failed to parse model response.", e);
        }
    }

    private HttpUrl chatCompletionsEndpoint(String baseUrl) {
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new ChatModelException("Invalid AI base_url: " + baseUrl);
        }

        String path = parsed.encodedPath();
        HttpUrl.Builder builder = parsed.newBuilder();
        if (path.endsWith("/chat/completions")) {
            return builder.build();
        }
        if (path.equals("/") || path.isBlank()) {
            builder.addPathSegment("v1");
        }
        return builder
                .addPathSegment("chat")
                .addPathSegment("completions")
                .build();
    }
}
