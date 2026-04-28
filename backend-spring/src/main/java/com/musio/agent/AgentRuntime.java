package com.musio.agent;

import com.musio.ai.SpringAiChatModelFactory;
import com.musio.config.MusioConfig;
import com.musio.config.MusioConfigService;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final AgentPrompts prompts;
    private final MusioConfigService configService;
    private final SpringAiChatModelFactory chatModelFactory;
    private final ToolRegistry toolRegistry;
    private final AgentEventBus eventBus;
    private final ConversationHistoryService conversationHistoryService;

    public AgentRuntime(
            AgentPrompts prompts,
            MusioConfigService configService,
            SpringAiChatModelFactory chatModelFactory,
            ToolRegistry toolRegistry,
            AgentEventBus eventBus,
            ConversationHistoryService conversationHistoryService
    ) {
        this.prompts = prompts;
        this.configService = configService;
        this.chatModelFactory = chatModelFactory;
        this.toolRegistry = toolRegistry;
        this.eventBus = eventBus;
        this.conversationHistoryService = conversationHistoryService;
    }

    public void start(String runId, ChatRequest request) {
        MusioConfig.Ai ai = configService.config().ai();
        AgentRunContext.setRunId(runId);
        try {
            String userId = conversationHistoryService.normalizeUserId(request.userId());
            List<ConversationHistoryMessage> history = conversationHistoryService.load(userId);
            Prompt prompt = conversationPrompt(history, request.message());

            String answer = chatModelFactory.chatClient(ai)
                    .prompt(prompt)
                    .toolCallbacks(toolRegistry.readOnlyToolCallbacks())
                    .call()
                    .content();
            String answerText = answer == null ? "" : answer;

            conversationHistoryService.appendTurn(userId, request.message(), answerText);

            eventBus.publish(runId, AgentEvent.of("agent_message_delta", Map.of(
                    "runId", runId,
                    "text", answerText,
                    "aiProvider", ai.provider(),
                    "aiModel", ai.model(),
                    "systemPromptLoaded", !prompts.systemPrompt().isBlank()
            )));
            eventBus.publish(runId, AgentEvent.of("done", Map.of("runId", runId)));
        } catch (Exception e) {
            log.warn("Agent run {} failed", runId, e);
            eventBus.publish(runId, AgentEvent.of("agent_error", Map.of(
                    "runId", runId,
                    "message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    "aiProvider", ai.provider(),
                    "aiModel", ai.model()
            )));
        } finally {
            AgentRunContext.clear();
        }
    }

    private Prompt conversationPrompt(List<ConversationHistoryMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(agentSystemPrompt()));
        for (ConversationHistoryMessage message : history) {
            if ("user".equals(message.role())) {
                messages.add(new UserMessage(message.content()));
            } else if ("assistant".equals(message.role())) {
                messages.add(new AssistantMessage(message.content()));
            }
        }
        messages.add(new UserMessage(userMessage));
        return new Prompt(messages);
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

    private String agentSystemPrompt() {
        String basePrompt = prompts.systemPrompt();
        String toolPolicy = """

                你可以调用只读音乐工具来搜索歌曲、读取歌曲详情、歌词、热门评论和歌单内容。
                当用户的问题需要真实音乐数据、歌词或评论时，先调用工具再回答。
                工具返回的数据是事实来源；不要编造不存在的歌曲、歌词、评论或播放链接。
                最终回答使用简洁中文，说明你参考了哪些搜索结果或工具数据。
                """;
        return (basePrompt == null ? "" : basePrompt) + toolPolicy;
    }
}
