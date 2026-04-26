package com.musio.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.agent.AgentRunContext;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
import com.musio.model.Comment;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.providers.MusicProviderGateway;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class MusicReadTools {
    private final MusicProviderGateway providerGateway;
    private final AgentEventBus eventBus;
    private final ObjectMapper objectMapper;

    public MusicReadTools(MusicProviderGateway providerGateway, AgentEventBus eventBus, ObjectMapper objectMapper) {
        this.providerGateway = providerGateway;
        this.eventBus = eventBus;
        this.objectMapper = objectMapper.findAndRegisterModules();
    }

    @Tool(name = "search_songs", description = "Search QQ Music songs by keyword. Use this when the user asks to find songs, recommend tracks, or discover music.")
    public String searchSongs(
            @ToolParam(description = "Search keyword, such as a song title, artist, mood, scene, or genre") String keyword,
            @ToolParam(description = "Maximum number of songs to return. Default 8, maximum 20") Integer limit) {
        int actualLimit = clamp(limit, 8, 1, 20);
        return runTool("search_songs", Map.of("keyword", keyword, "limit", actualLimit), () -> {
            List<Song> songs = providerGateway.defaultProvider().searchSongs(keyword, actualLimit);
            publish("song_cards", Map.of("songs", songs));
            return Map.of("success", true, "count", songs.size(), "songs", songs);
        });
    }

    @Tool(name = "get_song_detail", description = "Get details for one QQ Music song by provider-prefixed song id.")
    public String getSongDetail(
            @ToolParam(description = "Song id, preferably provider-prefixed, for example qqmusic:003OUlho2HcRHC") String songId) {
        return runTool("get_song_detail", Map.of("songId", songId), () -> {
            SongDetail detail = providerGateway.defaultProvider().getSongDetail(songId);
            return Map.of("success", true, "song", detail);
        });
    }

    @Tool(name = "get_lyrics", description = "Get lyrics for one QQ Music song by provider-prefixed song id.")
    public String getLyrics(
            @ToolParam(description = "Song id, preferably provider-prefixed, for example qqmusic:003OUlho2HcRHC") String songId) {
        return runTool("get_lyrics", Map.of("songId", songId), () -> {
            Lyrics lyrics = providerGateway.defaultProvider().getLyrics(songId);
            return Map.of("success", true, "lyrics", lyrics);
        });
    }

    @Tool(name = "get_hot_comments", description = "Get hot comments for one QQ Music song. Use this for comment analysis or listener sentiment.")
    public String getHotComments(
            @ToolParam(description = "Song id, preferably provider-prefixed, for example qqmusic:003OUlho2HcRHC") String songId,
            @ToolParam(description = "Maximum number of comments to return. Default 10, maximum 30") Integer limit) {
        int actualLimit = clamp(limit, 10, 1, 30);
        return runTool("get_hot_comments", Map.of("songId", songId, "limit", actualLimit), () -> {
            List<Comment> comments = providerGateway.defaultProvider().getComments(songId).stream()
                    .limit(actualLimit)
                    .toList();
            return Map.of("success", true, "count", comments.size(), "comments", comments);
        });
    }

    @Tool(name = "get_user_playlists", description = "Get the current QQ Music user's playlists. Use this only after the user has logged in.")
    public String getUserPlaylists(
            @ToolParam(description = "Maximum number of playlists to return. Default 20, maximum 50") Integer limit) {
        int actualLimit = clamp(limit, 20, 1, 50);
        return runTool("get_user_playlists", Map.of("limit", actualLimit), () -> {
            List<Playlist> playlists = providerGateway.defaultProvider().getPlaylists("local").stream()
                    .limit(actualLimit)
                    .toList();
            return Map.of("success", true, "count", playlists.size(), "playlists", playlists);
        });
    }

    @Tool(name = "get_playlist_songs", description = "Get songs in one QQ Music playlist by playlist id.")
    public String getPlaylistSongs(
            @ToolParam(description = "Playlist id, preferably provider-prefixed if available") String playlistId,
            @ToolParam(description = "Maximum number of songs to return. Default 20, maximum 50") Integer limit) {
        int actualLimit = clamp(limit, 20, 1, 50);
        return runTool("get_playlist_songs", Map.of("playlistId", playlistId, "limit", actualLimit), () -> {
            List<Song> songs = providerGateway.defaultProvider().getPlaylistSongs(playlistId).stream()
                    .limit(actualLimit)
                    .toList();
            publish("song_cards", Map.of("songs", songs));
            return Map.of("success", true, "count", songs.size(), "songs", songs);
        });
    }

    private String runTool(String toolName, Map<String, Object> input, Supplier<Map<String, Object>> action) {
        publish("tool_start", Map.of("tool", toolName, "input", input));
        try {
            Map<String, Object> result = action.get();
            publish("tool_result", Map.of(
                    "tool", toolName,
                    "summary", summary(toolName, result)
            ));
            return writeJson(result);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Map<String, Object> error = Map.of("success", false, "tool", toolName, "message", message);
            publish("tool_result", Map.of("tool", toolName, "summary", "工具执行失败: " + message));
            return writeJson(error);
        }
    }

    private void publish(String type, Map<String, Object> data) {
        AgentRunContext.runId().ifPresent(runId -> {
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("runId", runId);
            eventData.putAll(data);
            eventBus.publish(runId, AgentEvent.of(type, eventData));
        });
    }

    private String summary(String toolName, Map<String, Object> result) {
        Object count = result.get("count");
        if (count != null) {
            return toolName + " returned " + count + " item(s)";
        }
        return toolName + " completed";
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"message\":\"Failed to serialize tool result.\"}";
        }
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int actual = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, actual));
    }
}
