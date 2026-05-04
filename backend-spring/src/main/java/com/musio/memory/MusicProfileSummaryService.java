package com.musio.memory;

import com.musio.model.MusicGeneSnapshot;
import com.musio.model.MusicProfileMemory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MusicProfileSummaryService {
    public MusicProfileMemory summarize(MusicGeneSnapshot snapshot) {
        Map<String, Object> data = mapValue(snapshot.data());
        Map<String, Object> sourceSummary = mapValue(data.get("summary"));
        Map<String, Object> signals = mapValue(data.get("signals"));

        List<String> favoriteArtists = names(sourceSummary.get("top_artists"), 12);
        List<String> favoriteAlbums = names(sourceSummary.get("top_albums"), 8);
        List<String> likedSongExamples = likedSongExamples(signals.get("liked_songs"), 10);
        String confidence = text(sourceSummary.get("confidence"));

        List<String> strongPreferences = strongPreferences(favoriteArtists, favoriteAlbums, likedSongExamples);
        List<String> recommendationHints = recommendationHints(confidence, favoriteArtists, favoriteAlbums, likedSongExamples);

        return new MusicProfileMemory(
                snapshot.provider(),
                snapshot.accountKey(),
                snapshot.userId(),
                Instant.now(),
                snapshot.generatedAt(),
                summaryText(confidence, favoriteArtists, favoriteAlbums, likedSongExamples),
                strongPreferences,
                favoriteArtists,
                favoriteAlbums,
                likedSongExamples,
                recommendationHints,
                List.of(),
                sourceStats(sourceSummary)
        );
    }

    private String summaryText(
            String confidence,
            List<String> favoriteArtists,
            List<String> favoriteAlbums,
            List<String> likedSongExamples
    ) {
        List<String> parts = new ArrayList<>();
        String confidenceText = confidence.isBlank() ? "unknown" : confidence;
        parts.add("当前音乐画像置信度为 " + confidenceText + "。");
        if (!favoriteArtists.isEmpty()) {
            parts.add("高频歌手包括 " + joinTop(favoriteArtists, 6) + "。");
        }
        if (!favoriteAlbums.isEmpty()) {
            parts.add("常见专辑偏好包括 " + joinTop(favoriteAlbums, 4) + "。");
        }
        if (!likedSongExamples.isEmpty()) {
            parts.add("喜欢歌曲样本包括 " + joinTop(likedSongExamples, 4) + "。");
        }
        if (parts.size() == 1) {
            parts.add("当前数据较少，推荐时需要先搜索更多候选歌曲，并结合用户当下指令判断。");
        }
        return String.join("", parts);
    }

    private List<String> strongPreferences(
            List<String> favoriteArtists,
            List<String> favoriteAlbums,
            List<String> likedSongExamples
    ) {
        List<String> preferences = new ArrayList<>();
        if (!favoriteArtists.isEmpty()) {
            preferences.add("偏好歌手：" + joinTop(favoriteArtists, 6));
        }
        if (!favoriteAlbums.isEmpty()) {
            preferences.add("偏好专辑：" + joinTop(favoriteAlbums, 4));
        }
        if (!likedSongExamples.isEmpty()) {
            preferences.add("可参考的喜欢歌曲：" + joinTop(likedSongExamples, 5));
        }
        return List.copyOf(preferences);
    }

    private List<String> recommendationHints(
            String confidence,
            List<String> favoriteArtists,
            List<String> favoriteAlbums,
            List<String> likedSongExamples
    ) {
        List<String> hints = new ArrayList<>();
        if (!favoriteArtists.isEmpty()) {
            hints.add("个性化推荐时优先围绕高频歌手、关注歌手和相近歌手扩展。");
        }
        if (!favoriteAlbums.isEmpty()) {
            hints.add("可以从偏好专辑的歌手、制作风格和相邻作品中寻找候选歌曲。");
        }
        if (!likedSongExamples.isEmpty()) {
            hints.add("除非用户明确要重听，否则推荐发现型歌曲时应避免只重复已喜欢歌曲。");
        }
        if ("low".equalsIgnoreCase(confidence) || hints.isEmpty()) {
            hints.add("画像数据不足时，先询问用户当前场景或心情，再结合搜索结果推荐。");
        }
        hints.add("用户当前明确指令优先级高于长期画像。");
        return List.copyOf(hints);
    }

    private Map<String, Object> sourceStats(Map<String, Object> sourceSummary) {
        Map<String, Object> stats = new LinkedHashMap<>();
        copyIfPresent(sourceSummary, stats, "confidence");
        copyIfPresent(sourceSummary, stats, "liked_song_count");
        copyIfPresent(sourceSummary, stats, "created_playlist_count");
        copyIfPresent(sourceSummary, stats, "favorite_playlist_count");
        copyIfPresent(sourceSummary, stats, "favorite_album_count");
        copyIfPresent(sourceSummary, stats, "follow_singer_count");
        return stats;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private List<String> names(Object value, int limit) {
        List<String> names = new ArrayList<>();
        for (Object item : listValue(value)) {
            Map<String, Object> map = mapValue(item);
            String name = text(map.get("name"));
            if (!name.isBlank()) {
                names.add(name);
            }
            if (names.size() >= limit) {
                break;
            }
        }
        return List.copyOf(names);
    }

    private List<String> likedSongExamples(Object value, int limit) {
        List<String> examples = new ArrayList<>();
        for (Object item : listValue(value)) {
            Map<String, Object> song = mapValue(item);
            String title = text(song.get("title"));
            if (title.isBlank()) {
                continue;
            }
            List<String> artists = stringList(song.get("artists"), 3);
            examples.add(artists.isEmpty() ? title : title + " - " + String.join("/", artists));
            if (examples.size() >= limit) {
                break;
            }
        }
        return List.copyOf(examples);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private List<?> listValue(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private List<String> stringList(Object value, int limit) {
        List<String> result = new ArrayList<>();
        for (Object item : listValue(value)) {
            String text = text(item);
            if (!text.isBlank()) {
                result.add(text);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private String joinTop(List<String> values, int limit) {
        return String.join("、", values.stream().limit(limit).toList());
    }
}
