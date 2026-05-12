package com.musio.providers;

import com.musio.agent.capability.CapabilityEffect;
import com.musio.model.Comment;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MusicSourceDefaults {
    private MusicSourceDefaults() {
    }

    public static List<SourceCapability> readCapabilities() {
        return List.of(
                capability("search_songs", "搜索歌曲、歌手、专辑或候选音乐；excludedTitles 可选。", schema(
                        "keyword", "string",
                        "limit", "number",
                        "excludedTitles", "string[]"
                ), Set.of("keyword", "limit"), "songs"),
                capability("get_song_detail", "读取歌曲详情。", schema("songId", "string"), Set.of("songId"), "song_detail"),
                capability("get_song_url", "读取歌曲播放地址。", schema("songId", "string"), Set.of("songId"), "song_url"),
                capability("get_lyrics", "读取一首歌曲的歌词。", schema("songId", "string"), Set.of("songId"), "lyrics"),
                capability("get_hot_comments", "读取一首歌曲的热门评论。", schema(
                        "songId", "string",
                        "limit", "number"
                ), Set.of("songId"), "comments"),
                capability("get_user_playlists", "读取用户歌单。", schema("limit", "number"), Set.of(), "playlists"),
                capability("get_playlist_songs", "读取歌单歌曲。", schema(
                        "playlistId", "string",
                        "limit", "number"
                ), Set.of("playlistId"), "songs")
        );
    }

    public static Map<String, Object> executeLegacy(MusicProvider provider, SourceToolCall call) {
        String toolName = call == null ? "" : call.toolName();
        Map<String, Object> arguments = call == null ? Map.of() : call.arguments();
        return switch (toolName) {
            case "search_songs" -> {
                List<Song> songs = provider.searchSongs(text(arguments, "keyword"), integer(arguments, "limit", 5));
                yield result(provider, toolName, "songs", "count", songs.size(), "songs", songs);
            }
            case "get_song_detail" -> {
                SongDetail detail = provider.getSongDetail(text(arguments, "songId"));
                yield result(provider, toolName, "song_detail", "song", detail);
            }
            case "get_song_url" -> {
                SongUrl songUrl = provider.getSongUrl(text(arguments, "songId"));
                yield result(provider, toolName, "song_url", "songUrl", songUrl);
            }
            case "get_lyrics" -> {
                Lyrics lyrics = provider.getLyrics(text(arguments, "songId"));
                yield result(provider, toolName, "lyrics", "lyrics", lyrics);
            }
            case "get_hot_comments" -> {
                List<Comment> comments = provider.getComments(text(arguments, "songId"));
                yield result(provider, toolName, "comments", "count", comments.size(), "comments", comments);
            }
            case "get_user_playlists" -> {
                List<Playlist> playlists = provider.getPlaylists("local").stream()
                        .limit(integer(arguments, "limit", 20))
                        .toList();
                yield result(provider, toolName, "playlists", "count", playlists.size(), "playlists", playlists);
            }
            case "get_playlist_songs" -> {
                List<Song> songs = provider.getPlaylistSongs(text(arguments, "playlistId")).stream()
                        .limit(integer(arguments, "limit", 20))
                        .toList();
                yield result(provider, toolName, "songs", "count", songs.size(), "songs", songs);
            }
            default -> throw new IllegalArgumentException("Unknown source tool: " + toolName);
        };
    }

    private static SourceCapability capability(
            String name,
            String description,
            Map<String, Object> inputSchema,
            Set<String> required,
            String resultType
    ) {
        return new SourceCapability(name, CapabilityEffect.READ, description, inputSchema, required, true, "", resultType);
    }

    private static Map<String, Object> schema(Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            values.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return values;
    }

    private static Map<String, Object> result(MusicProvider provider, String toolName, String resultType, Object... keysAndValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("success", true);
        values.put("sourceId", provider.type().sourceId());
        values.put("toolName", toolName);
        values.put("resultType", resultType);
        for (int index = 0; index + 1 < keysAndValues.length; index += 2) {
            values.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return values;
    }

    private static String text(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value instanceof String text ? text.strip() : "";
    }

    private static int integer(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
