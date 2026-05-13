package com.musio.memory;

import com.musio.model.MusicProfileMemory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class MusicProfilePreferenceUpdater {
    private static final String LOCAL_SUMMARY_PREFIX = "本地动态偏好：";
    private static final String POSITIVE_PREFIX = "本地偏好聚合：";
    private static final String NEGATIVE_PREFIX = "本地负向偏好聚合：";
    private static final String LOCAL_HINT_PREFIX = "本地偏好提示：";

    private final PreferenceAggregator preferenceAggregator;
    private final PreferenceStore preferenceStore;
    private final MusicProfileMemoryStore profileStore;

    public MusicProfilePreferenceUpdater(
            PreferenceAggregator preferenceAggregator,
            PreferenceStore preferenceStore,
            MusicProfileMemoryStore profileStore
    ) {
        this.preferenceAggregator = preferenceAggregator;
        this.preferenceStore = preferenceStore;
        this.profileStore = profileStore;
    }

    public int refreshAll() {
        if (preferenceStore == null) {
            return 0;
        }
        int updated = 0;
        for (String userId : preferenceStore.userIds()) {
            updated += refreshUser(userId);
        }
        return updated;
    }

    public int refreshUser(String userId) {
        if (preferenceAggregator == null || profileStore == null) {
            return 0;
        }
        String normalizedUserId = normalizeUserId(userId);
        List<PreferenceItem> items = preferenceAggregator.aggregate(normalizedUserId);
        if (items.isEmpty()) {
            return 0;
        }
        List<MusicProfileMemory> profiles = profileStore.readAll();
        List<MusicProfileMemory> targets = targetProfiles(normalizedUserId, profiles);
        for (MusicProfileMemory profile : targets) {
            profileStore.write(apply(profile, items));
        }
        return targets.size();
    }

    MusicProfileMemory apply(MusicProfileMemory profile, List<PreferenceItem> items) {
        if (profile == null || items == null || items.isEmpty()) {
            return profile;
        }
        List<PreferenceItem> positive = items.stream()
                .filter(item -> "positive".equals(item.polarity()))
                .sorted(Comparator.comparingDouble(PreferenceItem::confidence).reversed())
                .limit(8)
                .toList();
        List<PreferenceItem> negative = items.stream()
                .filter(item -> "negative".equals(item.polarity()))
                .sorted(Comparator.comparingDouble(PreferenceItem::confidence).reversed())
                .limit(8)
                .toList();
        Instant updatedAt = items.stream()
                .map(PreferenceItem::updatedAt)
                .max(Instant::compareTo)
                .orElse(Instant.now());

        Map<String, Object> sourceStats = new LinkedHashMap<>(profile.sourceStats() == null ? Map.of() : profile.sourceStats());
        sourceStats.put("localPreferenceItemCount", items.size());
        sourceStats.put("localPreferenceUpdatedAt", updatedAt.toString());

        return new MusicProfileMemory(
                profile.provider(),
                profile.accountKey(),
                profile.userId(),
                updatedAt,
                profile.sourceGeneGeneratedAt(),
                summary(profile.summary(), positive, negative),
                merge(profile.strongPreferences(), POSITIVE_PREFIX, positive),
                profile.favoriteArtists(),
                profile.favoriteAlbums(),
                profile.likedSongExamples(),
                recommendationHints(profile.recommendationHints(), positive, negative),
                merge(profile.avoid(), NEGATIVE_PREFIX, negative),
                sourceStats
        );
    }

    private List<MusicProfileMemory> targetProfiles(String userId, List<MusicProfileMemory> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        List<MusicProfileMemory> directMatches = profiles.stream()
                .filter(profile -> userId.equals(normalizeUserId(profile.userId())))
                .toList();
        if (!directMatches.isEmpty()) {
            return directMatches;
        }
        if ("local".equals(userId)) {
            return profiles;
        }
        return List.of();
    }

    private String summary(String current, List<PreferenceItem> positive, List<PreferenceItem> negative) {
        String base = removeLocalSummary(current);
        String local = localSummary(positive, negative);
        if (local.isBlank()) {
            return base;
        }
        if (base.isBlank()) {
            return local;
        }
        return base + local;
    }

    private String localSummary(List<PreferenceItem> positive, List<PreferenceItem> negative) {
        List<String> parts = new ArrayList<>();
        if (!positive.isEmpty()) {
            parts.add("正向：" + labels(positive, 4));
        }
        if (!negative.isEmpty()) {
            parts.add("避开：" + labels(negative, 4));
        }
        if (parts.isEmpty()) {
            return "";
        }
        return LOCAL_SUMMARY_PREFIX + String.join("；", parts) + "。";
    }

    private String removeLocalSummary(String current) {
        String value = current == null ? "" : current.strip();
        int start = value.indexOf(LOCAL_SUMMARY_PREFIX);
        if (start < 0) {
            return value;
        }
        int end = value.indexOf('。', start);
        if (end < 0) {
            return value.substring(0, start).strip();
        }
        return (value.substring(0, start) + value.substring(end + 1)).strip();
    }

    private List<String> recommendationHints(
            List<String> current,
            List<PreferenceItem> positive,
            List<PreferenceItem> negative
    ) {
        List<String> values = withoutLocalPrefixes(current);
        if (!positive.isEmpty()) {
            values.add(LOCAL_HINT_PREFIX + "推荐时可优先参考：" + labels(positive, 5) + "。");
        }
        if (!negative.isEmpty()) {
            values.add(LOCAL_HINT_PREFIX + "推荐时应避开或降低权重：" + labels(negative, 5) + "。");
        }
        return distinct(values, 20);
    }

    private List<String> merge(List<String> current, String prefix, List<PreferenceItem> items) {
        List<String> values = withoutPrefix(current, prefix);
        for (PreferenceItem item : items) {
            values.add(line(prefix, item));
        }
        return distinct(values, 20);
    }

    private List<String> withoutLocalPrefixes(List<String> values) {
        return withoutPrefix(values, LOCAL_HINT_PREFIX);
    }

    private List<String> withoutPrefix(List<String> values, String prefix) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank() && !value.strip().startsWith(prefix)) {
                result.add(value.strip());
            }
        }
        return result;
    }

    private String line(String prefix, PreferenceItem item) {
        String evidence = item.evidence().isBlank() ? "" : "，证据：" + item.evidence();
        return "%s%s（置信度 %.2f%s）".formatted(prefix, item.label(), item.confidence(), evidence);
    }

    private String labels(List<PreferenceItem> items, int limit) {
        return String.join("、", items.stream()
                .limit(limit)
                .map(PreferenceItem::label)
                .filter(label -> label != null && !label.isBlank())
                .toList());
    }

    private List<String> distinct(List<String> values, int limit) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.strip());
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private String normalizeUserId(String userId) {
        return userId == null || userId.isBlank() ? "local" : userId.strip();
    }
}
