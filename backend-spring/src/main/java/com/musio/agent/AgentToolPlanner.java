package com.musio.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.ai.SpringAiChatModelFactory;
import com.musio.config.MusioConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class AgentToolPlanner {
    private static final Logger log = LoggerFactory.getLogger(AgentToolPlanner.class);
    private static final double MIN_CONFIDENCE = 0.55;
    private static final int MAX_TOOL_CALLS = 12;
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "search_songs",
            "get_user_music_profile",
            "get_song_detail",
            "get_lyrics",
            "get_hot_comments",
            "get_user_playlists",
            "get_playlist_songs"
    );

    private final SpringAiChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;

    public AgentToolPlanner(SpringAiChatModelFactory chatModelFactory, ObjectMapper objectMapper) {
        this.chatModelFactory = chatModelFactory;
        this.objectMapper = objectMapper;
    }

    public AgentToolPlan plan(MusioConfig.Ai ai, AgentTaskContext taskContext, String taskMemoryPreview) {
        if (chatModelFactory == null || !shouldPlan(taskContext)) {
            return AgentToolPlan.empty();
        }
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(plannerInstruction()),
                new UserMessage("""
                        当前任务上下文：
                        %s

                        当前任务记忆：
                        %s
                        """.formatted(taskContext.promptContext(), taskMemoryPreview == null || taskMemoryPreview.isBlank() ? "无" : taskMemoryPreview))
        ));
        try {
            String content = chatModelFactory.chatClient(ai)
                    .prompt(prompt)
                    .call()
                    .content();
            return parsePlan(content).orElseGet(AgentToolPlan::empty);
        } catch (Exception e) {
            log.warn("Agent tool planning failed", e);
            return AgentToolPlan.empty();
        }
    }

    Optional<AgentToolPlan> parsePlan(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(content.strip()));
            double confidence = root.path("confidence").asDouble(0.0);
            if (confidence < MIN_CONFIDENCE) {
                return Optional.empty();
            }
            JsonNode callsNode = root.path("toolCalls");
            if (!callsNode.isArray()) {
                callsNode = root.path("calls");
            }
            if (!callsNode.isArray()) {
                return Optional.of(AgentToolPlan.empty());
            }

            List<AgentToolCall> calls = new ArrayList<>();
            for (JsonNode callNode : callsNode) {
                parseCall(callNode).ifPresent(calls::add);
                if (calls.size() >= MAX_TOOL_CALLS) {
                    break;
                }
            }
            return Optional.of(new AgentToolPlan(calls, confidence));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<AgentToolCall> parseCall(JsonNode callNode) {
        if (!callNode.isObject()) {
            return Optional.empty();
        }
        String toolName = text(callNode, "toolName");
        if (toolName.isBlank()) {
            toolName = text(callNode, "name");
        }
        if (!ALLOWED_TOOLS.contains(toolName)) {
            return Optional.empty();
        }
        JsonNode argumentsNode = callNode.path("arguments");
        if (!argumentsNode.isObject()) {
            argumentsNode = callNode.path("args");
        }
        Map<String, Object> arguments = argumentsNode.isObject()
                ? objectMapper.convertValue(argumentsNode, new TypeReference<LinkedHashMap<String, Object>>() {
                })
                : Map.of();
        if (!requiredArgumentsPresent(toolName, arguments)) {
            return Optional.empty();
        }
        return Optional.of(new AgentToolCall(toolName, arguments));
    }

    private boolean shouldPlan(AgentTaskContext taskContext) {
        return taskContext != null && taskContext.agentTask();
    }

    private boolean requiredArgumentsPresent(String toolName, Map<String, Object> arguments) {
        return switch (toolName) {
            case "get_song_detail", "get_lyrics", "get_hot_comments" -> hasText(arguments, "songId");
            case "get_playlist_songs" -> hasText(arguments, "playlistId");
            case "search_songs" -> hasText(arguments, "keyword");
            default -> true;
        };
    }

    private boolean hasText(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof String text && !text.isBlank();
    }

    private String plannerInstruction() {
        return """
                你是 Musio 的工具规划器。只输出 JSON 对象，不要 markdown，不要解释。
                你的任务是根据当前音乐任务和任务记忆，决定本轮回答前是否需要调用只读音乐工具。

                可用工具：
                - search_songs {"keyword": string, "limit": number, "excludedTitles": string[]}：搜索歌曲、歌手、专辑或候选音乐；excludedTitles 可选。
                - get_user_music_profile {}：读取本地音乐画像摘要。
                - get_song_detail {"songId": string}：读取歌曲详情。
                - get_lyrics {"songId": string}：读取歌词。
                - get_hot_comments {"songId": string, "limit": number}：读取热门评论。
                - get_user_playlists {"limit": number}：读取用户歌单。
                - get_playlist_songs {"playlistId": string, "limit": number}：读取歌单歌曲。

                输出格式：
                {"toolCalls":[{"toolName":"工具名","arguments":{}}],"confidence":0.0到1.0}

                规则：
                - 只规划回答当前问题真正需要的工具；不需要工具时输出空数组。
                - 如果当前任务上下文有目标歌曲 ID，就优先用该 songId 作为歌曲类工具参数。
                - 不要编造 songId、playlistId 或用户没有提供的标识符。
                - 搜索类任务应规划 search_songs；keyword 只写正向搜索目标，limit 使用用户要求的数量，没有要求时默认 8。
                - 如果任务上下文要求避免重复歌曲，把这些歌名放入 search_songs.arguments.excludedTitles，不要拼进 keyword。
                - 推荐类任务不要把“深夜写代码”“适合睡前”等场景词直接当唯一搜索词；应先挑出具体歌曲查询，规划多个 search_songs，每个 limit=1，查询格式优先“歌曲名 歌手”。
                - 场景词、风格词可以作为理解推荐需求的依据，但不要只依赖一次泛场景搜索来完成推荐。
                - 个性化推荐可以先规划 get_user_music_profile，再规划 search_songs。
                - 歌曲评论、感人评论、听众感受类问题通常需要 get_hot_comments。
                - 歌曲背景、详情、专辑、时长、歌手信息类问题通常需要 get_song_detail。
                - 歌词含义、歌词片段、逐句解读类问题通常需要 get_lyrics。
                - 最多输出 12 个工具调用。
                - 不要输出 chain-of-thought。
                """;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().strip() : "";
    }

    private String extractJsonObject(String value) {
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }
}
