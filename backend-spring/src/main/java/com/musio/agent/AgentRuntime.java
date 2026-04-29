package com.musio.agent;

import com.musio.ai.SpringAiChatModelFactory;
import com.musio.config.MusioConfig;
import com.musio.config.MusioConfigService;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import com.musio.model.MusicProfileMemory;
import com.musio.memory.MusicProfileService;
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
    private final MusicProfileService musicProfileService;
    private final AgentEventBus eventBus;
    private final ConversationHistoryService conversationHistoryService;

    public AgentRuntime(
            AgentPrompts prompts,
            MusioConfigService configService,
            SpringAiChatModelFactory chatModelFactory,
            ToolRegistry toolRegistry,
            MusicProfileService musicProfileService,
            AgentEventBus eventBus,
            ConversationHistoryService conversationHistoryService
    ) {
        this.prompts = prompts;
        this.configService = configService;
        this.chatModelFactory = chatModelFactory;
        this.toolRegistry = toolRegistry;
        this.musicProfileService = musicProfileService;
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
                当前用户如果已经生成音乐画像记忆，系统消息中会提供一份短摘要；需要更完整的个性化偏好时调用 get_user_music_profile。
                当用户要求“按我的口味推荐”“推荐我可能喜欢的歌”“根据我的音乐基因推荐”等个性化推荐时，先参考音乐画像或调用 get_user_music_profile，再调用 search_songs 获取实时候选歌曲。
                用户当前明确指令优先级高于音乐画像记忆。
                当用户的问题需要真实音乐数据、歌词或评论时，先调用工具再回答。
                工具返回的数据是事实来源；不要编造不存在的歌曲、歌词、评论或播放链接。
                最终回答使用简洁中文，说明你参考了哪些搜索结果或工具数据。
                """;
        return (basePrompt == null ? "" : basePrompt) + musicProfileMemoryPrompt() + toolPolicy;
    }

    private String musicProfileMemoryPrompt() {
        return musicProfileService.readOrCreate()
                .map(this::musicProfilePrompt)
                .orElse("");
    }

    private String musicProfilePrompt(MusicProfileMemory profile) {
        return """

                当前用户音乐画像记忆（由登录后的音乐基因归纳得到，只作为长期偏好参考）：
                - 摘要：%s
                - 高频歌手：%s
                - 偏好专辑：%s
                - 推荐提示：%s
                """.formatted(
                profile.summary(),
                joinLimited(profile.favoriteArtists(), 8),
                joinLimited(profile.favoriteAlbums(), 5),
                joinLimited(profile.recommendationHints(), 4)
        );
    }

    private String joinLimited(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "暂无";
        }
        return String.join("；", values.stream().limit(limit).toList());
    }
}
