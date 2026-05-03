package com.musio.api;

import com.musio.agent.AgentRunService;
import com.musio.model.ChatHistoryMessage;
import com.musio.model.ChatRequest;
import com.musio.model.ChatRunResponse;
import com.musio.model.PendingConfirmation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final AgentRunService agentRunService;

    public ChatController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @PostMapping
    public ChatRunResponse createRun(@Valid @RequestBody ChatRequest request) {
        return agentRunService.startRun(request);
    }

    @GetMapping("/runs/{runId}/events")
    public SseEmitter events(@PathVariable String runId) {
        return agentRunService.connect(runId);
    }

    @PostMapping("/runs/{runId}/confirm")
    public ChatRunResponse confirm(@PathVariable String runId, @RequestBody PendingConfirmation confirmation) {
        return agentRunService.confirm(runId, confirmation);
    }

    @PostMapping("/runs/{runId}/cancel")
    public ChatRunResponse cancel(@PathVariable String runId) {
        return agentRunService.cancel(runId);
    }

    @GetMapping("/history/{userId}")
    public List<ChatHistoryMessage> history(@PathVariable String userId) {
        return agentRunService.history(userId);
    }

    @DeleteMapping("/history/{userId}")
    public ChatRunResponse clearHistory(@PathVariable String userId) {
        return agentRunService.clearHistory(userId);
    }
}
