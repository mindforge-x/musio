package com.musio.memory;

import com.musio.agent.AgentGoal;
import com.musio.agent.loop.AgentLoopEvidence;
import com.musio.memory.context.MemoryContextPackage;

import java.time.Instant;

public record MemoryWriteRequest(
        String userId,
        String userMessage,
        AgentGoal goal,
        MemoryContextPackage memoryContext,
        AgentLoopEvidence loopEvidence,
        String finalAnswer,
        Instant occurredAt
) {
    public MemoryWriteRequest {
        userId = userId == null || userId.isBlank() ? "local" : userId.strip();
        userMessage = userMessage == null ? "" : userMessage.strip();
        memoryContext = memoryContext == null ? MemoryContextPackage.empty() : memoryContext;
        loopEvidence = loopEvidence == null ? AgentLoopEvidence.empty() : loopEvidence;
        finalAnswer = finalAnswer == null ? "" : finalAnswer.strip();
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
