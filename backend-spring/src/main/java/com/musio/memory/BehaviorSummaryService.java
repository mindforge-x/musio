package com.musio.memory;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class BehaviorSummaryService {
    private final BehaviorEventStore behaviorEventStore;
    private final PreferenceStore preferenceStore;

    public BehaviorSummaryService(BehaviorEventStore behaviorEventStore, PreferenceStore preferenceStore) {
        this.behaviorEventStore = behaviorEventStore;
        this.preferenceStore = preferenceStore;
    }

    public BehaviorSummary summarize(String userId) {
        return summarize(userId, Instant.now());
    }

    public BehaviorSummary summarize(String userId, Instant now) {
        String normalizedUserId = userId == null || userId.isBlank() ? "local" : userId.strip();
        Instant upper = now == null ? Instant.now() : now;
        List<BehaviorEvent> last24Hours = behaviorEventStore == null
                ? List.of()
                : behaviorEventStore.recent(normalizedUserId, upper.minus(Duration.ofDays(1)), 100);
        List<BehaviorEvent> last7Days = behaviorEventStore == null
                ? List.of()
                : behaviorEventStore.recent(normalizedUserId, upper.minus(Duration.ofDays(7)), 300);
        return new BehaviorSummary(
                normalizedUserId,
                eventSummary("最近 24 小时", last24Hours),
                eventSummary("最近 7 天", last7Days),
                negativeSignals(normalizedUserId),
                sceneSignals(last7Days),
                upper
        );
    }

    private String eventSummary(String label, List<BehaviorEvent> events) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        LinkedHashSet<String> songTitles = new LinkedHashSet<>();
        for (BehaviorEvent event : events) {
            counts.merge(event.type(), 1, Integer::sum);
            if (!event.songTitle().isBlank()) {
                songTitles.add(event.songTitle());
            }
        }
        String typeSummary = String.join("，", counts.entrySet().stream()
                .map(entry -> eventTypeLabel(entry.getKey()) + " " + entry.getValue() + " 次")
                .toList());
        String songs = songTitles.isEmpty() ? "" : "；涉及歌曲：" + String.join("、", songTitles.stream().limit(8).toList());
        return label + "：" + typeSummary + songs;
    }

    private List<String> negativeSignals(String userId) {
        if (preferenceStore == null) {
            return List.of();
        }
        List<String> fromItems = preferenceStore.items(userId, 20).stream()
                .filter(item -> "negative".equals(item.polarity()))
                .map(item -> item.label() + "（置信度 " + String.format(java.util.Locale.ROOT, "%.2f", item.confidence()) + "）")
                .toList();
        if (!fromItems.isEmpty()) {
            return fromItems;
        }
        return preferenceStore.candidates(userId, Instant.now().minus(Duration.ofDays(7)), 50).stream()
                .filter(candidate -> "negative".equals(candidate.polarity()))
                .map(PreferenceCandidate::label)
                .distinct()
                .limit(8)
                .toList();
    }

    private List<String> sceneSignals(List<BehaviorEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (BehaviorEvent event : events) {
            Object scene = event.payload().get("scene");
            if (scene instanceof String text && !text.isBlank()) {
                values.add(text.strip());
            }
            Object request = event.payload().get("request");
            if (request instanceof String text) {
                if (text.contains("写代码")) {
                    values.add("写代码/专注场景");
                }
                if (text.contains("晚上") || text.contains("夜")) {
                    values.add("夜间场景");
                }
                if (text.contains("通勤")) {
                    values.add("通勤场景");
                }
            }
        }
        return values.stream().limit(8).toList();
    }

    private String eventTypeLabel(String type) {
        return switch (type == null ? "" : type) {
            case "recommendation_shown" -> "推荐展示";
            case "search_performed" -> "搜索";
            case "comments_read" -> "评论读取";
            case "lyrics_read" -> "歌词读取";
            case "song_detail_read" -> "详情读取";
            case "local_playlist_add" -> "本地收藏";
            case "tool_failure" -> "工具失败";
            case "session_preference" -> "临时偏好";
            case "user_feedback" -> "用户反馈";
            default -> type == null || type.isBlank() ? "行为" : type;
        };
    }
}
