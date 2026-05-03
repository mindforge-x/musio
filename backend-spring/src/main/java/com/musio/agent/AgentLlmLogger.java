package com.musio.agent;

import com.musio.config.MusioConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

public final class AgentLlmLogger {
    private static final Logger log = LoggerFactory.getLogger("com.musio.agent.llm");

    private AgentLlmLogger() {
    }

    public static void logRequest(String stage, MusioConfig.Ai ai, Prompt prompt) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info("""
                LLM_REQUEST_BEGIN stage={} runId={} userId={} provider={} model={}
                {}
                LLM_REQUEST_END stage={} runId={}
                """,
                safe(stage),
                currentRunId(),
                currentUserId(),
                ai == null ? "" : safe(ai.provider()),
                ai == null ? "" : safe(ai.model()),
                promptText(prompt),
                safe(stage),
                currentRunId()
        );
    }

    public static void logResponse(String stage, MusioConfig.Ai ai, String response) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info("""
                LLM_RESPONSE_BEGIN stage={} runId={} userId={} provider={} model={}
                {}
                LLM_RESPONSE_END stage={} runId={}
                """,
                safe(stage),
                currentRunId(),
                currentUserId(),
                ai == null ? "" : safe(ai.provider()),
                ai == null ? "" : safe(ai.model()),
                response == null ? "" : response,
                safe(stage),
                currentRunId()
        );
    }

    public static void logStreamChunk(String stage, MusioConfig.Ai ai, String chunk) {
        logResponse(stage + ".chunk", ai, chunk);
    }

    private static String promptText(Prompt prompt) {
        if (prompt == null) {
            return "";
        }
        List<Message> messages = prompt.getInstructions();
        if (messages == null || messages.isEmpty()) {
            return prompt.getContents();
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            builder.append("message[").append(index).append("] role=")
                    .append(message == null || message.getMessageType() == null ? "unknown" : message.getMessageType().name())
                    .append('\n');
            builder.append(message == null ? "" : safe(message.getText()))
                    .append("\n\n");
        }
        return builder.toString().strip();
    }

    private static String currentRunId() {
        return AgentRunContext.runId().orElse("-");
    }

    private static String currentUserId() {
        return AgentRunContext.userId().orElse("-");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
