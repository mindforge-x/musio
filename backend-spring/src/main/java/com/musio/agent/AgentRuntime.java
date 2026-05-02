package com.musio.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.ai.SpringAiChatModelFactory;
import com.musio.agent.trace.AgentTracePublisher;
import com.musio.config.MusioConfig;
import com.musio.config.MusioConfigService;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import com.musio.model.MusicProfileMemory;
import com.musio.model.AgentTaskMemory;
import com.musio.memory.AgentTaskMemoryService;
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
    private final AgentTaskMemoryService taskMemoryService;
    private final AgentTaskContextResolver taskContextResolver;
    private final AgentTracePublisher tracePublisher;
    private final AgentToolPlanner toolPlanner;
    private final AgentToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public AgentRuntime(
            AgentPrompts prompts,
            MusioConfigService configService,
            SpringAiChatModelFactory chatModelFactory,
            ToolRegistry toolRegistry,
            MusicProfileService musicProfileService,
            AgentEventBus eventBus,
            ConversationHistoryService conversationHistoryService,
            AgentTaskMemoryService taskMemoryService,
            AgentTaskContextResolver taskContextResolver,
            AgentTracePublisher tracePublisher,
            AgentToolPlanner toolPlanner,
            AgentToolExecutor toolExecutor,
            ObjectMapper objectMapper
    ) {
        this.prompts = prompts;
        this.configService = configService;
        this.chatModelFactory = chatModelFactory;
        this.toolRegistry = toolRegistry;
        this.musicProfileService = musicProfileService;
        this.eventBus = eventBus;
        this.conversationHistoryService = conversationHistoryService;
        this.taskMemoryService = taskMemoryService;
        this.taskContextResolver = taskContextResolver;
        this.tracePublisher = tracePublisher;
        this.toolPlanner = toolPlanner;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    public void start(String runId, ChatRequest request) {
        MusioConfig.Ai ai = configService.config().ai();
        AgentRunContext.setRunId(runId);
        try {
            String userId = conversationHistoryService.normalizeUserId(request.userId());
            AgentRunContext.setUserId(userId);
            List<ConversationHistoryMessage> history = conversationHistoryService.load(userId);
            AgentTaskMemory taskMemory = taskMemoryService.read(userId);
            AgentTaskContext taskContext = taskContextResolver.resolve(ai, request.message(), history, taskMemory);
            boolean traceEnabled = taskContext.agentTask();
            AgentRunContext.setTraceEnabled(traceEnabled);
            if (traceEnabled) {
                tracePublisher.publishIntentRunning(runId);
                tracePublisher.publishIntentDone(runId);
                taskMemoryService.recordTask(
                        userId,
                        taskContext.planningMessage(),
                        taskContext.searchKeyword(),
                        taskContext.searchLimit(),
                        taskContext.avoidSongTitles(),
                        taskContext.preservePreviousSongContext()
                );
            }
            PreludeContext preludeContext = traceEnabled ? plannedToolPreludeContext(ai, taskContext, taskMemory) : PreludeContext.empty();
            Prompt prompt = conversationPrompt(history, request.message(), taskContext.promptContext(), preludeContext.text());

            StringBuilder answer = new StringBuilder();
            boolean[] composeStarted = {false};
            var chatRequest = chatModelFactory.chatClient(ai)
                    .prompt(prompt);
            if (preludeContext.allowToolCallbacks()) {
                chatRequest = chatRequest.toolCallbacks(toolRegistry.readOnlyToolCallbacks());
            }
            chatRequest
                    .stream()
                    .content()
                    .doOnNext(chunk -> publishAnswerDelta(runId, ai, answer, chunk, traceEnabled, composeStarted))
                    .blockLast();
            String answerText = answer.toString();
            if (traceEnabled) {
                if (!composeStarted[0]) {
                    tracePublisher.publishComposeRunning(runId);
                }
                tracePublisher.publishComposeDone(runId);
            }

            conversationHistoryService.appendTurn(userId, request.message(), answerText);

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

    private void publishAnswerDelta(String runId, MusioConfig.Ai ai, StringBuilder answer, String chunk, boolean traceEnabled, boolean[] composeStarted) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        if (traceEnabled && !composeStarted[0]) {
            tracePublisher.publishComposeRunning(runId);
            composeStarted[0] = true;
        }
        answer.append(chunk);
        eventBus.publish(runId, AgentEvent.of("agent_message_delta", Map.of(
                "runId", runId,
                "text", chunk,
                "aiProvider", ai.provider(),
                "aiModel", ai.model(),
                "systemPromptLoaded", !prompts.systemPrompt().isBlank()
        )));
    }

    private Prompt conversationPrompt(
            List<ConversationHistoryMessage> history,
            String userMessage,
            String taskContext,
            String preludeContext
    ) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(agentSystemPrompt() + taskContext + preludeContext));
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

    private PreludeContext plannedToolPreludeContext(MusioConfig.Ai ai, AgentTaskContext taskContext, AgentTaskMemory taskMemory) {
        AgentToolPlan plan = toolPlanner.plan(ai, taskContext, taskContextResolver.taskMemoryPreview(taskMemory));
        List<AgentToolExecution> executions = toolExecutor.execute(plan);
        if (executions.isEmpty()) {
            if (taskContext.toolEvidenceExpected()) {
                return new PreludeContext("""

                        本轮工具规划器没有产生可执行的真实工具结果。
                        最终回答不得声称“已读取 QQ 音乐详情/评论/歌词/歌单”等没有真实发生的工具调用。
                        如果仍需要这些信息，可以在回答阶段调用只读音乐工具；否则必须明确说明本轮没有拿到对应真实工具结果。
                        """, true);
            }
            return PreludeContext.empty();
        }
        return new PreludeContext("""

                本轮工具规划器已根据任务上下文决定并执行这些只读音乐工具：
                %s
                最终回答必须基于这些真实工具结果；不要声称调用了没有出现在上方结果里的工具。
                """.formatted(toolExecutionContext(executions)), false);
    }

    private String toolExecutionContext(List<AgentToolExecution> executions) {
        StringBuilder builder = new StringBuilder();
        for (AgentToolExecution execution : executions) {
            builder.append("工具：").append(execution.toolName()).append('\n');
            builder.append("参数：").append(writeJson(execution.arguments())).append('\n');
            builder.append("结果 JSON：").append(execution.resultJson()).append("\n\n");
        }
        return builder.toString().strip();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record PreludeContext(String text, boolean allowToolCallbacks) {
        static PreludeContext empty() {
            return new PreludeContext("", true);
        }
    }

    public AgentEvent describeConfiguration(String runId, ChatRequest request) {
        MusioConfig.Ai ai = configService.config().ai();
        return AgentEvent.of("agent_message_delta", Map.of(
                "runId", runId,
                "text", "Agent runtime is initialized with model " + ai.model() + ". Music tool planning is enabled.",
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
                当用户的问题需要真实音乐数据、歌词、歌曲详情或评论时，先调用相应工具再回答。
                如果本轮上下文提供了目标歌曲 ID，评论类请求优先调用 get_hot_comments，歌词类请求优先调用 get_lyrics，详情/背景类请求优先调用 get_song_detail。
                工具返回的数据是事实来源；不要编造不存在的歌曲、歌词、评论或播放链接。
                只有本轮上下文已经提供了真实工具结果，或你实际调用了对应工具并获得结果，才可以说“我查了/读取了/参考了”。
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
