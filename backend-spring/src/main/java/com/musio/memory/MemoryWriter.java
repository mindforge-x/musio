package com.musio.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.agent.AgentRunContext;
import com.musio.agent.capability.AgentCapabilityRegistry;
import com.musio.agent.loop.AgentLoopEvidence;
import com.musio.agent.loop.AgentObservation;
import com.musio.agent.loop.AgentObservationStatus;
import com.musio.agent.recommendation.RecommendationSlot;
import com.musio.agent.recommendation.RecommendationSlots;
import com.musio.model.AgentTaskMemory;
import com.musio.model.AgentTaskRecommendationSlot;
import com.musio.model.Song;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class MemoryWriter {
    private static final int MAX_CACHE_CONTENT_CHARS = 6000;
    private static final int MAX_SUMMARY_CHARS = 1200;

    private final BehaviorEventStore behaviorEventStore;
    private final PreferenceStore preferenceStore;
    private final MusicCacheStore musicCacheStore;
    private final ConversationSummaryStore conversationSummaryStore;
    private final AgentTaskMemoryService taskMemoryService;
    private final ObjectMapper objectMapper;

    public MemoryWriter(
            BehaviorEventStore behaviorEventStore,
            PreferenceStore preferenceStore,
            MusicCacheStore musicCacheStore,
            ConversationSummaryStore conversationSummaryStore,
            ObjectMapper objectMapper
    ) {
        this(behaviorEventStore, preferenceStore, musicCacheStore, conversationSummaryStore, objectMapper, null);
    }

    @Autowired
    public MemoryWriter(
            BehaviorEventStore behaviorEventStore,
            PreferenceStore preferenceStore,
            MusicCacheStore musicCacheStore,
            ConversationSummaryStore conversationSummaryStore,
            ObjectMapper objectMapper,
            AgentTaskMemoryService taskMemoryService
    ) {
        this.behaviorEventStore = behaviorEventStore;
        this.preferenceStore = preferenceStore;
        this.musicCacheStore = musicCacheStore;
        this.conversationSummaryStore = conversationSummaryStore;
        this.taskMemoryService = taskMemoryService;
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper.findAndRegisterModules();
    }

    public MemoryWritePlan writeAfterTurn(MemoryWriteRequest request) {
        MemoryWritePlan plan = plan(request);
        for (BehaviorEvent event : plan.behaviorEvents()) {
            if (behaviorEventStore != null) {
                behaviorEventStore.append(event);
            }
        }
        for (PreferenceCandidate candidate : plan.preferenceCandidates()) {
            if (preferenceStore != null) {
                preferenceStore.addCandidate(candidate);
            }
        }
        for (MusicCacheEntry entry : plan.musicCacheEntries()) {
            if (musicCacheStore != null) {
                musicCacheStore.upsert(entry);
            }
        }
        for (ConversationSummary summary : plan.conversationSummaries()) {
            if (conversationSummaryStore != null) {
                conversationSummaryStore.upsert(summary);
            }
        }
        for (AgentTaskMemoryUpdate update : plan.taskMemoryUpdates()) {
            if (taskMemoryService != null) {
                taskMemoryService.applyTurnUpdate(request.userId(), update);
            }
        }
        return plan;
    }

    public MemoryWritePlan plan(MemoryWriteRequest request) {
        if (request == null) {
            return MemoryWritePlan.empty();
        }
        List<BehaviorEvent> events = new ArrayList<>();
        List<PreferenceCandidate> candidates = List.of();
        events.addAll(behaviorEvents(request));
        List<MusicCacheEntry> cacheEntries = musicCacheEntries(request);
        List<ConversationSummary> summaries = conversationSummaries(request);
        List<AgentTaskMemoryUpdate> taskMemoryUpdates = taskMemoryUpdates(request);
        return new MemoryWritePlan(events, candidates, cacheEntries, summaries, taskMemoryUpdates);
    }

    private List<BehaviorEvent> behaviorEvents(MemoryWriteRequest request) {
        AgentLoopEvidence evidence = request.loopEvidence();
        if (evidence == null || evidence.observations().isEmpty()) {
            return List.of();
        }
        List<BehaviorEvent> events = new ArrayList<>();
        for (AgentObservation observation : evidence.observations()) {
            if (observation.status() != AgentObservationStatus.SUCCESS) {
                events.add(eventForObservation(request, observation, "tool_failure", null));
                continue;
            }
            String eventType = eventType(observation.toolName());
            if (eventType.isBlank()) {
                continue;
            }
            List<Song> songs = observation.songs().isEmpty() ? evidence.songs() : observation.songs();
            if (songs.isEmpty()) {
                events.add(eventForObservation(request, observation, eventType, null));
                continue;
            }
            for (Song song : songs.stream().limit(20).toList()) {
                events.add(eventForObservation(request, observation, eventType, song));
            }
        }
        return events;
    }

    private BehaviorEvent eventForObservation(MemoryWriteRequest request, AgentObservation observation, String eventType, Song song) {
        return new BehaviorEvent(
                "",
                request.userId(),
                eventType,
                observation.toolName(),
                song == null ? songIdFromArguments(observation.arguments()) : safe(song.id()),
                song == null ? "" : safe(song.title()),
                song == null ? List.of() : song.artists(),
                observation.plannerSummary(),
                basePayload(request, Map.of(
                        "toolName", observation.toolName(),
                        "status", observation.status().name(),
                        "arguments", observation.arguments(),
                        "resultSummary", resultSummary(observation.resultJson())
                )),
                observation.status() == AgentObservationStatus.SUCCESS ? 0.9 : 0.75,
                request.occurredAt()
        );
    }

    private List<MusicCacheEntry> musicCacheEntries(MemoryWriteRequest request) {
        AgentLoopEvidence evidence = request.loopEvidence();
        if (evidence == null || evidence.observations().isEmpty()) {
            return List.of();
        }
        List<MusicCacheEntry> entries = new ArrayList<>();
        for (AgentObservation observation : evidence.observations()) {
            if (observation.status() != AgentObservationStatus.SUCCESS) {
                continue;
            }
            entries.addAll(cacheEntriesForObservation(request, observation));
        }
        return entries;
    }

    private List<MusicCacheEntry> cacheEntriesForObservation(MemoryWriteRequest request, AgentObservation observation) {
        if (observation == null || observation.resultJson().isBlank()) {
            return List.of();
        }
        if ("get_hot_comments".equals(observation.toolName())) {
            return commentCacheEntries(request, observation);
        }
        String cacheType = cacheType(observation.toolName());
        if (cacheType.isBlank()) {
            return List.of();
        }
        CacheMetadata metadata = cacheMetadata(request, observation);
        return List.of(new MusicCacheEntry(
                "",
                request.userId(),
                cacheType,
                metadata.songId(),
                metadata.title(),
                metadata.artist(),
                limit(resultSummary(observation.resultJson()) + "\n" + observation.resultJson(), MAX_CACHE_CONTENT_CHARS),
                cacheEvidence(observation, metadata),
                request.occurredAt()
        ));
    }

    private List<MusicCacheEntry> commentCacheEntries(MemoryWriteRequest request, AgentObservation observation) {
        List<MusicCacheEntry> entries = new ArrayList<>();
        CacheMetadata metadata = cacheMetadata(request, observation);
        String rawContent = limit(observation.resultJson(), MAX_CACHE_CONTENT_CHARS);
        String summaryContent = limit(commentSummary(observation.resultJson()), MAX_SUMMARY_CHARS);
        if (!rawContent.isBlank()) {
            entries.add(new MusicCacheEntry(
                    "",
                    request.userId(),
                    "comments",
                    metadata.songId(),
                    metadata.title(),
                    metadata.artist(),
                    rawContent,
                    cacheEvidence(observation, metadata),
                    request.occurredAt()
            ));
        }
        if (!summaryContent.isBlank()) {
            entries.add(new MusicCacheEntry(
                    "",
                    request.userId(),
                    "commentSummary",
                    metadata.songId(),
                    metadata.title(),
                    metadata.artist(),
                    summaryContent,
                    cacheEvidence(observation, metadata),
                    request.occurredAt()
            ));
        }
        return List.copyOf(entries);
    }

    private List<ConversationSummary> conversationSummaries(MemoryWriteRequest request) {
        if (request.userMessage().isBlank() && request.finalAnswer().isBlank()) {
            return List.of();
        }
        String summary = limit("""
                用户：%s
                助手：%s
                """.formatted(request.userMessage(), request.finalAnswer()), MAX_SUMMARY_CHARS);
        List<String> keywords = new ArrayList<>();
        if (request.goal() != null) {
            keywords.add(request.goal().taskType());
            keywords.add(request.goal().contextMode());
            keywords.add(request.goal().effectiveRequest());
            keywords.addAll(request.goal().requiredOutcomes().stream().map(Enum::name).toList());
        }
        return List.of(new ConversationSummary("", request.userId(), summary, keywords, request.occurredAt()));
    }

    private List<AgentTaskMemoryUpdate> taskMemoryUpdates(MemoryWriteRequest request) {
        AgentLoopEvidence evidence = request.loopEvidence();
        if (evidence == null || !evidence.hasObservations()) {
            return List.of();
        }
        return List.of(new AgentTaskMemoryUpdate(
                evidence.songs(),
                evidence.targetSong(),
                evidence.completedTaskType(),
                evidence.observationSummaries(),
                true,
                request.goal() == null
                        ? List.of()
                        : request.goal().requiredOutcomes().stream().map(Enum::name).toList(),
                request.goal() == null
                        ? List.of()
                        : recommendationSlotMemories(request.goal().recommendationSlots(), evidence),
                successfulToolNames(evidence),
                request.goal() != null && request.goal().localWriteIntent()
                        ? List.of(AgentCapabilityRegistry.ADD_SONG_TO_MUSIO_PLAYLIST)
                        : List.of(),
                true
        ));
    }

    private List<String> successfulToolNames(AgentLoopEvidence evidence) {
        if (evidence == null || evidence.observations().isEmpty()) {
            return List.of();
        }
        return evidence.observations().stream()
                .filter(observation -> observation.status() == AgentObservationStatus.SUCCESS)
                .map(AgentObservation::toolName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    private List<AgentTaskRecommendationSlot> recommendationSlotMemories(List<RecommendationSlot> recommendationSlots, AgentLoopEvidence evidence) {
        List<RecommendationSlot> slots = RecommendationSlots.normalize(recommendationSlots);
        if (slots.isEmpty() || evidence == null || evidence.observations().isEmpty()) {
            return List.of();
        }
        Map<String, SlotSongRefs> refsBySlot = new LinkedHashMap<>();
        for (RecommendationSlot slot : slots) {
            refsBySlot.put(slot.slotId(), new SlotSongRefs(new LinkedHashSet<>(), new LinkedHashSet<>()));
        }
        String singleSlotId = slots.size() == 1 ? slots.getFirst().slotId() : "";
        for (AgentObservation observation : evidence.observations()) {
            if (observation.status() != AgentObservationStatus.SUCCESS
                    || !AgentCapabilityRegistry.RECOMMEND_SONGS.equals(observation.toolName())
                    || observation.resultJson().isBlank()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(observation.resultJson());
                readRecommendationSlotSongs(root.path("songs"), refsBySlot, singleSlotId);
                readRecommendationSlotSongs(root.path("recommendations"), refsBySlot, singleSlotId);
            } catch (Exception ignored) {
                // The observation summary remains the durable fallback if structured slot parsing fails.
            }
        }
        List<AgentTaskRecommendationSlot> values = new ArrayList<>();
        for (RecommendationSlot slot : slots) {
            SlotSongRefs refs = refsBySlot.getOrDefault(slot.slotId(), new SlotSongRefs(new LinkedHashSet<>(), new LinkedHashSet<>()));
            values.add(new AgentTaskRecommendationSlot(
                    slot.slotId(),
                    slot.targetType(),
                    slot.target(),
                    slot.count(),
                    List.copyOf(refs.songIds()),
                    List.copyOf(refs.songTitles())
            ));
        }
        return values;
    }

    private void readRecommendationSlotSongs(JsonNode node, Map<String, SlotSongRefs> refsBySlot, String fallbackSlotId) {
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            String slotId = item.path("slotId").asText("");
            if (slotId.isBlank()) {
                slotId = fallbackSlotId;
            }
            SlotSongRefs refs = refsBySlot.get(slotId);
            if (refs == null) {
                continue;
            }
            String songId = item.path("id").asText(item.path("songId").asText(""));
            String title = item.path("title").asText("");
            if (!songId.isBlank()) {
                refs.songIds().add(songId.strip());
            }
            if (!title.isBlank()) {
                refs.songTitles().add(title.strip());
            }
        }
    }

    private Map<String, Object> basePayload(MemoryWriteRequest request, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        AgentRunContext.runId().ifPresent(runId -> payload.put("runId", runId));
        payload.put("request", request.userMessage());
        if (request.goal() != null) {
            payload.put("taskType", request.goal().taskType());
            payload.put("contextMode", request.goal().contextMode());
        }
        if (extra != null) {
            payload.putAll(extra);
        }
        return payload;
    }

    private String eventType(String toolName) {
        return switch (toolName == null ? "" : toolName) {
            case AgentCapabilityRegistry.RECOMMEND_SONGS -> "recommendation_shown";
            case "search_songs" -> "search_performed";
            case "get_hot_comments" -> "comments_read";
            case "get_lyrics" -> "lyrics_read";
            case "get_song_detail" -> "song_detail_read";
            case AgentCapabilityRegistry.ADD_SONG_TO_MUSIO_PLAYLIST -> "local_playlist_add";
            default -> "";
        };
    }

    private String cacheType(String toolName) {
        return switch (toolName == null ? "" : toolName) {
            case "get_lyrics" -> "lyricsSummary";
            case "get_song_detail" -> "songDetail";
            default -> "";
        };
    }

    private String resultSummary(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            String summary = root.path("summary").asText("");
            if (!summary.isBlank()) {
                return summary.strip();
            }
            String message = root.path("message").asText("");
            if (!message.isBlank()) {
                return message.strip();
            }
        } catch (Exception ignored) {
        }
        return limit(resultJson, 500);
    }

    private String commentSummary(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            String summary = root.path("summary").asText("");
            if (!summary.isBlank()) {
                return summary.strip();
            }
            String message = root.path("message").asText("");
            if (!message.isBlank()) {
                return message.strip();
            }
            List<String> comments = new ArrayList<>();
            collectCommentTexts(root.path("comments"), comments);
            JsonNode commentResults = root.path("commentResults");
            if (commentResults.isArray()) {
                for (JsonNode item : commentResults) {
                    collectCommentTexts(item.path("comments"), comments);
                }
            }
            if (!comments.isEmpty()) {
                return "评论摘录：" + String.join("；", comments.stream().limit(5).toList());
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void collectCommentTexts(JsonNode commentsNode, List<String> target) {
        if (commentsNode == null || !commentsNode.isArray() || target == null) {
            return;
        }
        for (JsonNode comment : commentsNode) {
            String text = comment.path("text").asText("");
            if (!text.isBlank()) {
                target.add(text.strip());
            }
        }
    }

    private CacheMetadata cacheMetadata(MemoryWriteRequest request, AgentObservation observation) {
        String argumentSongId = songIdFromArguments(observation == null ? null : observation.arguments());
        Song song = cacheSong(request, observation, argumentSongId);
        String songId = firstNonBlank(argumentSongId, song == null ? "" : song.id(), resultSongId(observation == null ? "" : observation.resultJson()));
        String title = firstNonBlank(song == null ? "" : song.title(), titleFromObservation(observation));
        String artist = firstNonBlank(artistFromSong(song), resultArtist(observation == null ? "" : observation.resultJson()));
        return new CacheMetadata(songId, title, artist);
    }

    private Song cacheSong(MemoryWriteRequest request, AgentObservation observation, String songId) {
        List<Song> candidates = new ArrayList<>();
        if (observation != null && observation.songs() != null) {
            candidates.addAll(observation.songs());
        }
        AgentLoopEvidence evidence = request == null ? null : request.loopEvidence();
        if (evidence != null) {
            if (evidence.targetSong() != null) {
                candidates.add(evidence.targetSong());
            }
            candidates.addAll(evidence.songs());
        }
        AgentTaskMemory taskMemory = request == null ? null : request.taskMemory();
        if (taskMemory != null) {
            if (taskMemory.lastTargetSong() != null) {
                candidates.add(taskMemory.lastTargetSong());
            }
            candidates.addAll(taskMemory.lastResultSongs());
        }
        for (Song candidate : candidates) {
            if (candidate != null && !safe(songId).isBlank() && safe(songId).equals(safe(candidate.id()))) {
                return candidate;
            }
        }
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.id() != null && !candidate.id().isBlank())
                .findFirst()
                .orElse(null);
    }

    private String cacheEvidence(AgentObservation observation, CacheMetadata metadata) {
        String summary = observation == null ? "" : observation.plannerSummary();
        if (metadata == null || metadata.isEmpty()) {
            return summary;
        }
        List<String> parts = new ArrayList<>();
        if (!metadata.songId().isBlank()) {
            parts.add("songId=" + metadata.songId());
        }
        if (!metadata.title().isBlank()) {
            parts.add("title=" + metadata.title());
        }
        if (!metadata.artist().isBlank()) {
            parts.add("artist=" + metadata.artist());
        }
        String suffix = String.join(" ", parts);
        return summary.isBlank() ? suffix : summary + "\n" + suffix;
    }

    private String titleFromObservation(AgentObservation observation) {
        if (observation == null) {
            return "";
        }
        if (observation.songs() != null && !observation.songs().isEmpty()) {
            return safe(observation.songs().getFirst().title());
        }
        return resultTitle(observation.resultJson());
    }

    private String resultTitle(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson == null ? "{}" : resultJson);
            String title = root.path("title").asText("");
            if (!title.isBlank()) {
                return title.strip();
            }
            JsonNode song = root.path("song");
            if (song.isObject()) {
                return song.path("title").asText("").strip();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String resultSongId(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson == null ? "{}" : resultJson);
            String songId = root.path("songId").asText("");
            if (!songId.isBlank()) {
                return songId.strip();
            }
            JsonNode song = root.path("song");
            if (song.isObject()) {
                return song.path("id").asText(song.path("songId").asText("")).strip();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String resultArtist(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson == null ? "{}" : resultJson);
            String artist = root.path("artist").asText("");
            if (!artist.isBlank()) {
                return artist.strip();
            }
            String artists = artistsFromNode(root.path("artists"));
            if (!artists.isBlank()) {
                return artists;
            }
            JsonNode song = root.path("song");
            if (song.isObject()) {
                artist = song.path("artist").asText("");
                if (!artist.isBlank()) {
                    return artist.strip();
                }
                return artistsFromNode(song.path("artists"));
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String artistsFromNode(JsonNode node) {
        if (node == null || !node.isArray()) {
            return "";
        }
        List<String> artists = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                artists.add(item.asText().strip());
            }
        }
        return String.join(" / ", artists);
    }

    private String artistFromSong(Song song) {
        if (song == null || song.artists() == null || song.artists().isEmpty()) {
            return "";
        }
        return String.join(" / ", song.artists().stream()
                .filter(artist -> artist != null && !artist.isBlank())
                .map(String::strip)
                .toList());
    }

    private String songIdFromArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        Object songId = arguments.get("songId");
        if (songId instanceof String text) {
            return text.strip();
        }
        Object songIds = arguments.get("songIds");
        if (songIds instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof String text) {
            return text.strip();
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String limit(String value, int maxChars) {
        String safe = value == null ? "" : value.strip();
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxChars - 3)).strip() + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private record CacheMetadata(String songId, String title, String artist) {
        private CacheMetadata {
            songId = songId == null ? "" : songId.strip();
            title = title == null ? "" : title.strip();
            artist = artist == null ? "" : artist.strip();
        }

        private boolean isEmpty() {
            return songId.isBlank() && title.isBlank() && artist.isBlank();
        }
    }

    private record SlotSongRefs(LinkedHashSet<String> songIds, LinkedHashSet<String> songTitles) {
    }
}
