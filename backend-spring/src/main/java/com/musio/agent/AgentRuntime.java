package com.musio.agent;

import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentRuntime {
    private final AgentPrompts prompts;

    public AgentRuntime(AgentPrompts prompts) {
        this.prompts = prompts;
    }

    public AgentEvent start(String runId, ChatRequest request) {
        return AgentEvent.of("agent_message_delta", Map.of(
                "runId", runId,
                "text", "Agent runtime is initialized. Spring AI tool calling will be wired in the next phase.",
                "systemPromptLoaded", !prompts.systemPrompt().isBlank(),
                "userMessage", request.message()
        ));
    }
}
