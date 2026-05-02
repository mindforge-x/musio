package com.musio.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.ai.SpringAiChatModelFactory;
import com.musio.agent.recommendation.RecommendationOrchestrator;
import com.musio.agent.recommendation.RecommendationResponse;
import com.musio.agent.trace.AgentTracePublisher;
import com.musio.config.MusioConfig;
import com.musio.config.MusioConfigService;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
import com.musio.model.ChatRequest;
import com.musio.model.MusicProfileMemory;
import com.musio.model.AgentTaskMemory;
import com.musio.model.Song;
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
    private final MusicProfileService musicProfileService;
    private final AgentEventBus eventBus;
    private final ConversationHistoryService conversationHistoryService;
    private final AgentTaskMemoryService taskMemoryService;
    private final AgentTaskContextResolver taskContextResolver;
    private final AgentTracePublisher tracePublisher;
    private final AgentToolPlanner toolPlanner;
    private final AgentToolExecutor toolExecutor;
    private final RecommendationOrchestrator recommendationOrchestrator;
    private final ObjectMapper objectMapper;

    public AgentRuntime(
            AgentPrompts prompts,
            MusioConfigService configService,
            SpringAiChatModelFactory chatModelFactory,
            MusicProfileService musicProfileService,
            AgentEventBus eventBus,
            ConversationHistoryService conversationHistoryService,
            AgentTaskMemoryService taskMemoryService,
            AgentTaskContextResolver taskContextResolver,
            AgentTracePublisher tracePublisher,
            AgentToolPlanner toolPlanner,
            AgentToolExecutor toolExecutor,
            RecommendationOrchestrator recommendationOrchestrator,
            ObjectMapper objectMapper
    ) {
        this.prompts = prompts;
        this.configService = configService;
        this.chatModelFactory = chatModelFactory;
        this.musicProfileService = musicProfileService;
        this.eventBus = eventBus;
        this.conversationHistoryService = conversationHistoryService;
        this.taskMemoryService = taskMemoryService;
        this.taskContextResolver = taskContextResolver;
        this.tracePublisher = tracePublisher;
        this.toolPlanner = toolPlanner;
        this.toolExecutor = toolExecutor;
        this.recommendationOrchestrator = recommendationOrchestrator;
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
            if (traceEnabled && taskContext.recommendationPreludeAllowed()) {
                publishRecommendationRun(runId, userId, ai, request, taskContext, taskMemory, traceEnabled);
                return;
            }
            PreludeContext preludeContext = traceEnabled ? plannedToolPreludeContext(ai, taskContext, taskMemory) : PreludeContext.empty();
            Prompt prompt = conversationPrompt(history, request.message(), taskContext.promptContext(), preludeContext);

            AgentAnswerStreamGuard answerGuard = new AgentAnswerStreamGuard();
            boolean[] composeStarted = {false};
            chatModelFactory.chatClient(ai)
                    .prompt(prompt)
                    .stream()
                    .content()
                    .doOnNext(chunk -> publishAnswerDelta(runId, ai, answerGuard, chunk, traceEnabled, composeStarted))
                    .blockLast();
            answerGuard.finish(rawToolProtocolFallback()).ifPresent(text -> publishAnswerText(runId, ai, text, traceEnabled, composeStarted));
            String answerText = answerGuard.visibleAnswer();
            if (answerGuard.rawToolProtocolSuppressed()) {
                log.warn("Agent run {} suppressed raw tool protocol output from model {}", runId, ai.model());
            }
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

    private void publishRecommendationRun(
            String runId,
            String userId,
            MusioConfig.Ai ai,
            ChatRequest request,
            AgentTaskContext taskContext,
            AgentTaskMemory taskMemory,
            boolean traceEnabled
    ) {
        RecommendationResponse response = recommendationOrchestrator.recommend(
                ai,
                taskContext.planningMessage(),
                taskContext.searchLimit(),
                taskContext.avoidSongTitles(),
                taskMemory
        );
        boolean[] composeStarted = {false};
        publishAnswerText(runId, ai, response.answerText(), traceEnabled, composeStarted);
        publishSongCards(runId, response.songs());
        if (traceEnabled) {
            if (!composeStarted[0]) {
                tracePublisher.publishComposeRunning(runId);
            }
            tracePublisher.publishComposeDone(runId);
        }
        conversationHistoryService.appendTurn(userId, request.message(), response.answerText());
        eventBus.publish(runId, AgentEvent.of("done", Map.of("runId", runId)));
    }

    private void publishAnswerDelta(String runId, MusioConfig.Ai ai, AgentAnswerStreamGuard answerGuard, String chunk, boolean traceEnabled, boolean[] composeStarted) {
        answerGuard.accept(chunk).ifPresent(text -> publishAnswerText(runId, ai, text, traceEnabled, composeStarted));
    }

    private void publishAnswerText(String runId, MusioConfig.Ai ai, String text, boolean traceEnabled, boolean[] composeStarted) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (traceEnabled && !composeStarted[0]) {
            tracePublisher.publishComposeRunning(runId);
            composeStarted[0] = true;
        }
        eventBus.publish(runId, AgentEvent.of("agent_message_delta", Map.of(
                "runId", runId,
                "text", text,
                "aiProvider", ai.provider(),
                "aiModel", ai.model(),
                "systemPromptLoaded", !prompts.systemPrompt().isBlank()
        )));
    }

    private void publishSongCards(String runId, List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return;
        }
        eventBus.publish(runId, AgentEvent.of("song_cards", Map.of(
                "runId", runId,
                "songs", songs
        )));
    }

    private Prompt conversationPrompt(
            List<ConversationHistoryMessage> history,
            String userMessage,
            String taskContext,
            PreludeContext preludeContext
    ) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(agentSystemPrompt() + taskContext + preludeContext.text()));
        if (preludeContext.evidenceBound()) {
            messages.add(new UserMessage("""
                    当前用户输入：
                    %s

                    请只根据系统消息中的本轮任务上下文和本轮工具 evidence 回答。
                    最近历史已经只用于 Router/Planner 判断上下文，不得把历史 assistant 文本当成本轮事实来源。
                    """.formatted(userMessage)));
        } else {
            for (ConversationHistoryMessage message : history) {
                if ("user".equals(message.role())) {
                    messages.add(new UserMessage(message.content()));
                } else if ("assistant".equals(message.role())) {
                    messages.add(new AssistantMessage(message.content()));
                }
            }
            messages.add(new UserMessage(userMessage));
        }
        return new Prompt(messages);
    }

    private PreludeContext plannedToolPreludeContext(MusioConfig.Ai ai, AgentTaskContext taskContext, AgentTaskMemory taskMemory) {
        AgentToolPlan plan = toolPlanner.plan(ai, taskContext, taskContextResolver.plannerTaskMemoryPreview(taskContext, taskMemory));
        List<AgentToolExecution> executions = toolExecutor.execute(plan);
        if (executions.isEmpty()) {
            if (taskContext.toolEvidenceExpected()) {
                return new PreludeContext("""

                        本轮工具规划器没有产生可执行的真实工具结果。
                        最终回答不得声称“已读取 QQ 音乐详情/评论/歌词/歌单”等没有真实发生的工具调用。
                        最终回答阶段不能再调用工具，也不能输出 <tool_call>、<function=...>、JSON 工具调用或任何工具调用协议文本。
                        必须直接用自然语言回答；如果缺少真实工具结果，请明确说明本轮没有拿到对应真实工具结果。
                        """, false);
            }
            return PreludeContext.empty();
        }
        return new PreludeContext("""

                本轮工具规划器已根据任务上下文决定并执行这些只读音乐工具：
                %s
                最终回答必须基于这些真实工具结果；不要声称调用了没有出现在上方结果里的工具。
                当前工具结果优先于历史对话、任务记忆和上一轮失败描述；不要把历史里的 HTTP 500 当成本轮结果。
                工具状态以“状态”行和 result.success 为准：success=true 的工具不得写成失败、HTTP 500 或没有拿到结果。
                如果工具成功但结果相关性不高，应说明“QQ 音乐返回结果不够准确”，不要改写成接口失败。
                如果 success=true 且返回了歌曲，正文必须承认本轮拿到了这些实时搜索结果；可以评价相关性，但不能说“没有拿到实时结果”。
                如果 search_songs 返回了歌曲，正文列出的主推荐歌曲必须来自本轮结果 JSON；不要一边展示工具歌曲卡片，一边正文列出另一批未查询歌曲。
                工具调用阶段已经结束；最终回答阶段不能再调用工具，也不能输出 <tool_call>、<function=...>、JSON 工具调用或任何工具调用协议文本。
                你必须直接生成面向用户的中文自然语言回答。
                """.formatted(toolExecutionContext(executions)), true);
    }

    private String toolExecutionContext(List<AgentToolExecution> executions) {
        StringBuilder builder = new StringBuilder();
        for (AgentToolExecution execution : executions) {
            builder.append("工具：").append(execution.toolName()).append('\n');
            builder.append("参数：").append(writeJson(execution.arguments())).append('\n');
            builder.append("状态：").append(toolResultStatus(execution.resultJson())).append('\n');
            builder.append("结果 JSON：").append(execution.resultJson()).append("\n\n");
        }
        return builder.toString().strip();
    }

    private String toolResultStatus(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return "未知，工具没有返回 JSON 文本";
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode successNode = root.path("success");
            if (successNode.isBoolean() && successNode.asBoolean()) {
                List<String> parts = new ArrayList<>();
                parts.add("成功");
                JsonNode countNode = root.path("count");
                if (countNode.isNumber()) {
                    parts.add("返回 " + countNode.asInt() + " 条结果");
                }
                String resultPreview = toolResultPreview(root);
                if (!resultPreview.isBlank()) {
                    parts.add(resultPreview);
                }
                return String.join("，", parts);
            }
            if (successNode.isBoolean()) {
                String message = root.path("message").isTextual() ? root.path("message").asText() : "未提供错误原因";
                return "失败，原因：" + message;
            }
            return "未知，结果 JSON 没有 success 字段";
        } catch (Exception e) {
            return "未知，结果 JSON 解析失败";
        }
    }

    private String toolResultPreview(JsonNode root) {
        JsonNode songs = root.path("songs");
        if (songs.isArray() && songs.size() > 0) {
            List<String> previews = new ArrayList<>();
            for (JsonNode song : songs) {
                String title = song.path("title").asText("");
                String artists = artistPreview(song.path("artists"));
                if (!title.isBlank()) {
                    previews.add(artists.isBlank() ? title : title + " - " + artists);
                }
                if (previews.size() >= 5) {
                    break;
                }
            }
            if (!previews.isEmpty()) {
                return "歌曲：" + String.join("；", previews);
            }
        }
        JsonNode comments = root.path("comments");
        if (comments.isArray()) {
            return "评论 " + comments.size() + " 条";
        }
        JsonNode playlists = root.path("playlists");
        if (playlists.isArray()) {
            return "歌单 " + playlists.size() + " 个";
        }
        return "";
    }

    private String artistPreview(JsonNode artists) {
        if (!artists.isArray() || artists.size() == 0) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode artist : artists) {
            if (artist.isTextual() && !artist.asText().isBlank()) {
                values.add(artist.asText());
            }
            if (values.size() >= 3) {
                break;
            }
        }
        return String.join("/", values);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record PreludeContext(String text, boolean evidenceBound) {
        static PreludeContext empty() {
            return new PreludeContext("", false);
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

                Musio 的工具调用由独立工具规划器在最终回答前完成；你处在最终回答阶段。
                你不能在最终回答中发起工具调用，也不能输出 <tool_call>、<function=...>、JSON 工具调用或任何工具调用协议文本。
                如果系统消息提供了工具结果，它们就是事实来源；如果没有工具结果，就明确说明没有拿到对应真实工具结果。
                当前用户如果已经生成音乐画像记忆，系统消息中会提供一份短摘要，作为长期偏好参考。
                用户当前明确指令优先级高于音乐画像记忆。
                工具返回的数据是事实来源；不要编造不存在的歌曲、歌词、评论或播放链接。
                只有本轮上下文已经提供了真实工具结果，才可以说“我查了/读取了/参考了”。
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

    private String rawToolProtocolFallback() {
        return "这次模型返回了工具调用协议文本，而不是正常回答。Musio 已阻止这段内部协议展示给你；请重试一次，或切回支持工具调用/JSON 规划更稳定的模型。";
    }

}
