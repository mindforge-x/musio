package com.musio.agent.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecommendationSlots {
    private static final int MAX_TOTAL = 10;
    private static final Pattern COUNT_TARGET_SONG = Pattern.compile("(?<!第)([一二两三四五六七八九十\\d]{1,3})(首|支|曲|个)([^，,。；;、和并]{1,20}?)的?(?:歌|歌曲|音乐|曲子)");
    private static final Pattern TARGET_COUNT_SONG = Pattern.compile("([^，,。；;、和并]{1,20}?)([一二两三四五六七八九十\\d]{1,3})(首|支|曲|个)(?:歌|歌曲|音乐|曲子)");
    private static final Pattern EXPLICIT_COUNT = Pattern.compile("(?<!第)([一二两三四五六七八九十\\d]{1,3})(首|支|曲|个)");

    private RecommendationSlots() {
    }

    public static List<RecommendationSlot> normalize(List<RecommendationSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        Map<String, RecommendationSlot> merged = new LinkedHashMap<>();
        int total = 0;
        for (RecommendationSlot slot : slots) {
            if (slot == null || slot.target().isBlank() || slot.count() <= 0 || total >= MAX_TOTAL) {
                continue;
            }
            int acceptedCount = Math.min(slot.count(), MAX_TOTAL - total);
            String key = normalizeKey(slot.targetType()) + "|" + normalizeKey(slot.target());
            RecommendationSlot existing = merged.get(key);
            if (existing == null) {
                merged.put(key, new RecommendationSlot(slot.slotId(), slot.targetType(), slot.target(), acceptedCount));
            } else {
                merged.put(key, new RecommendationSlot(existing.slotId(), existing.targetType(), existing.target(), existing.count() + acceptedCount));
            }
            total += acceptedCount;
        }
        return List.copyOf(merged.values());
    }

    public static List<RecommendationSlot> fromArgument(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<RecommendationSlot> slots = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof RecommendationSlot slot) {
                slots.add(slot);
                continue;
            }
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String target = text(map.get("target"));
            if (target.isBlank()) {
                target = text(map.get("artist"));
            }
            if (target.isBlank()) {
                target = text(map.get("keyword"));
            }
            if (target.isBlank()) {
                continue;
            }
            String targetType = text(map.get("targetType"));
            if (targetType.isBlank() && map.containsKey("artist")) {
                targetType = "artist";
            }
            int count = integer(map.get("count"));
            if (count <= 0) {
                continue;
            }
            slots.add(new RecommendationSlot(text(map.get("slotId")), targetType, target, count));
        }
        return normalize(slots);
    }

    public static List<RecommendationSlot> fromMessage(String userMessage) {
        String text = userMessage == null ? "" : userMessage.replaceAll("\\s+", "");
        if (text.isBlank()) {
            return List.of();
        }
        List<RecommendationSlot> slots = new ArrayList<>();
        readCountTargetSlots(text, COUNT_TARGET_SONG, 3, 1, slots);
        readCountTargetSlots(text, TARGET_COUNT_SONG, 1, 2, slots);
        return normalize(slots);
    }

    public static int explicitSongCount(String userMessage) {
        String text = userMessage == null ? "" : userMessage.replaceAll("\\s+", "");
        if (text.isBlank()) {
            return 0;
        }
        Matcher matcher = EXPLICIT_COUNT.matcher(text);
        int total = 0;
        while (matcher.find() && total < 20) {
            if ("个".equals(matcher.group(2)) && !songNounNearby(text, matcher.end())) {
                continue;
            }
            int count = parseCount(matcher.group(1));
            if (count > 0) {
                total += count;
            }
        }
        return Math.max(0, Math.min(20, total));
    }

    private static boolean songNounNearby(String text, int start) {
        String tail = text.substring(Math.min(start, text.length()), Math.min(text.length(), start + 12));
        return tail.contains("歌") || tail.contains("音乐") || tail.contains("曲子");
    }

    public static int totalCount(List<RecommendationSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return 0;
        }
        return slots.stream()
                .filter(slot -> slot != null)
                .mapToInt(RecommendationSlot::count)
                .sum();
    }

    public static List<Map<String, Object>> toArgument(List<RecommendationSlot> slots) {
        List<RecommendationSlot> normalized = normalize(slots);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (RecommendationSlot slot : normalized) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("slotId", slot.slotId());
            value.put("targetType", slot.targetType());
            value.put("target", slot.target());
            value.put("count", slot.count());
            values.add(value);
        }
        return List.copyOf(values);
    }

    public static String summary(List<RecommendationSlot> slots) {
        List<RecommendationSlot> normalized = normalize(slots);
        if (normalized.isEmpty()) {
            return "none";
        }
        return String.join("；", normalized.stream()
                .map(slot -> "%s:%s=%s x%s".formatted(slot.slotId(), slot.targetType(), slot.target(), slot.count()))
                .toList());
    }

    static String slotId(String targetType, String target) {
        String source = (safe(targetType).isBlank() ? "artist" : safe(targetType)) + "_" + safe(target);
        StringBuilder builder = new StringBuilder();
        source.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(Character.toLowerCase(codePoint));
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
            }
        });
        String value = builder.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
        return value.isBlank() ? "slot" : value;
    }

    private static void readCountTargetSlots(
            String text,
            Pattern pattern,
            int targetGroup,
            int countGroup,
            List<RecommendationSlot> slots
    ) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int count = parseCount(matcher.group(countGroup));
            String target = cleanTarget(matcher.group(targetGroup));
            if (count <= 0 || !looksLikeArtistTarget(target)) {
                continue;
            }
            slots.add(new RecommendationSlot("", "artist", target, count));
        }
    }

    private static boolean looksLikeArtistTarget(String target) {
        String value = safe(target);
        if (value.isBlank() || value.length() > 12) {
            return false;
        }
        List<String> blocked = List.of("适合", "深夜", "学习", "写代码", "治愈", "风格", "听", "推荐", "热评", "歌词", "评论", "歌单", "加入", "获取", "收藏");
        return blocked.stream().noneMatch(value::contains);
    }

    private static String cleanTarget(String target) {
        String value = safe(target)
                .replaceFirst("^(推荐|再推荐|再来|来|给我|帮我|要|想听|请)$", "")
                .replaceFirst("^(推荐|再推荐|再来|来|给我|帮我|要|想听|请)", "");
        return value.strip();
    }

    private static int parseCount(String value) {
        String text = safe(value);
        if (text.isBlank()) {
            return 0;
        }
        try {
            return Math.max(1, Math.min(MAX_TOTAL, Integer.parseInt(text)));
        } catch (NumberFormatException ignored) {
            return chineseCount(text);
        }
    }

    private static int chineseCount(String text) {
        return switch (text) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            case "十一" -> 11;
            case "十二" -> 12;
            case "十三" -> 13;
            case "十四" -> 14;
            case "十五" -> 15;
            case "十六" -> 16;
            case "十七" -> 17;
            case "十八" -> 18;
            case "十九" -> 19;
            case "二十" -> 20;
            default -> 0;
        };
    }

    private static String text(Object value) {
        return value instanceof String text ? text.strip() : "";
    }

    private static int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return parseCount(text.strip());
        }
        return 0;
    }

    private static String normalizeKey(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
