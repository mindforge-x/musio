package com.musio.agent;

import com.musio.ai.ChatCompletionRequest;
import com.musio.ai.ChatCompletionResult;
import com.musio.ai.ChatMessage;
import com.musio.ai.ChatModelClient;
import com.musio.ai.ChatModelException;
import com.musio.config.MusioConfig;
import com.musio.config.MusioConfigService;
import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AgentRuntime {
    private final AgentPrompts prompts;
    private final MusioConfigService configService;
    private final ChatModelClient chatModelClient;

    public AgentRuntime(AgentPrompts prompts, MusioConfigService configService, ChatModelClient chatModelClient) {
        this.prompts = prompts;
        this.configService = configService;
        this.chatModelClient = chatModelClient;
    }

    public AgentEvent start(String runId, ChatRequest request) {
        MusioConfig.Ai ai = configService.config().ai();
        try {
            ChatCompletionResult result = chatModelClient.complete(new ChatCompletionRequest(
                    List.of(
                            ChatMessage.system(prompts.systemPrompt()),
                            ChatMessage.user(request.message())
                    ),
                    ai
            ));

            return AgentEvent.of("agent_message_delta", Map.of(
                    "runId", runId,
                    "text", result.text(),
                    "aiProvider", result.provider(),
                    "aiModel", result.model(),
                    "systemPromptLoaded", !prompts.systemPrompt().isBlank()
            ));
        } catch (ChatModelException e) {
            return AgentEvent.of("agent_error", Map.of(
                    "runId", runId,
                    "message", e.getMessage(),
                    "aiProvider", ai.provider(),
                    "aiModel", ai.model()
            ));
        }
    }

    public AgentEvent describeConfiguration(String runId, ChatRequest request) {
        MusioConfig.Ai ai = configService.config().ai();
        return AgentEvent.of("agent_message_delta", Map.of(
                "runId", runId,
                "text", "Agent runtime is initialized with model " + ai.model() + ". Spring AI tool calling will be wired in the next phase.",
                "aiProvider", ai.provider(),
                "aiModel", ai.model(),
                "systemPromptLoaded", !prompts.systemPrompt().isBlank(),
                "userMessage", request.message()
        ));
    }
}
