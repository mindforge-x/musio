package com.musio.agent.recommendation;

import com.musio.agent.AgentRunContext;
import com.musio.agent.trace.AgentTracePublisher;
import com.musio.config.MusioConfig;
import com.musio.memory.AgentTaskMemoryService;
import com.musio.memory.context.MemoryContextPackage;
import com.musio.model.AgentRecentRecommendedSong;
import com.musio.model.AgentTaskMemory;
import com.musio.model.Song;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RecommendationOrchestrator {
    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_COUNT = 10;

    private final RecommendationDraftGenerator draftGenerator;
    private final SongResolver songResolver;
    private final AgentTracePublisher tracePublisher;
    private final AgentTaskMemoryService taskMemoryService;

    public RecommendationOrchestrator(
            RecommendationDraftGenerator draftGenerator,
            SongResolver songResolver,
            AgentTracePublisher tracePublisher,
            AgentTaskMemoryService taskMemoryService
    ) {
        this.draftGenerator = draftGenerator;
        this.songResolver = songResolver;
        this.tracePublisher = tracePublisher;
        this.taskMemoryService = taskMemoryService;
    }

    public RecommendationResponse recommend(
            MusioConfig.Ai ai,
            String userRequest,
            int requestedCount,
            List<String> avoidSongTitles,
            AgentTaskMemory taskMemory
    ) {
        int count = requestedCount(requestedCount);
        AgentRunContext.runId().ifPresent(tracePublisher::publishRecommendationRunning);
        return draftGenerator.generate(ai, userRequest, count, avoidSongTitles, taskMemory)
                .map(draft -> resolve(draft, count, userRequest, avoidSongTitles))
                .orElseGet(this::draftFailure);
    }

    public RecommendationResponse recommend(
            MusioConfig.Ai ai,
            String userRequest,
            int requestedCount,
            List<String> avoidSongTitles,
            AgentTaskMemory taskMemory,
            MemoryContextPackage memoryContext
    ) {
        int count = requestedCount(requestedCount);
        AgentRunContext.runId().ifPresent(tracePublisher::publishRecommendationRunning);
        return draftGenerator.generate(ai, userRequest, count, avoidSongTitles, taskMemory, memoryContext)
                .map(draft -> resolve(draft, count, userRequest, avoidSongTitles))
                .orElseGet(this::draftFailure);
    }

    public RecommendationResponse recommend(
            MusioConfig.Ai ai,
            String userRequest,
            List<RecommendationSlot> recommendationSlots,
            List<String> avoidSongTitles,
            AgentTaskMemory taskMemory
    ) {
        List<RecommendationSlot> slots = RecommendationSlots.normalize(recommendationSlots);
        if (slots.isEmpty()) {
            return recommend(ai, userRequest, DEFAULT_COUNT, avoidSongTitles, taskMemory);
        }
        int count = requestedCount(RecommendationSlots.totalCount(slots));
        AgentRunContext.runId().ifPresent(tracePublisher::publishRecommendationRunning);
        return draftGenerator.generate(ai, userRequest, slots, avoidSongTitles, taskMemory)
                .map(draft -> resolve(draft, count, slots, userRequest, avoidSongTitles))
                .orElseGet(this::draftFailure);
    }

    public RecommendationResponse recommend(
            MusioConfig.Ai ai,
            String userRequest,
            List<RecommendationSlot> recommendationSlots,
            List<String> avoidSongTitles,
            AgentTaskMemory taskMemory,
            MemoryContextPackage memoryContext
    ) {
        List<RecommendationSlot> slots = RecommendationSlots.normalize(recommendationSlots);
        if (slots.isEmpty()) {
            return recommend(ai, userRequest, DEFAULT_COUNT, avoidSongTitles, taskMemory, memoryContext);
        }
        int count = requestedCount(RecommendationSlots.totalCount(slots));
        AgentRunContext.runId().ifPresent(tracePublisher::publishRecommendationRunning);
        return draftGenerator.generate(ai, userRequest, slots, avoidSongTitles, taskMemory, memoryContext)
                .map(draft -> resolve(draft, count, slots, userRequest, avoidSongTitles))
                .orElseGet(this::draftFailure);
    }

    private RecommendationResponse resolve(RecommendationDraft draft, int requestedCount, String userRequest, List<String> avoidSongTitles) {
        return resolve(draft, requestedCount, List.of(), userRequest, avoidSongTitles);
    }

    private RecommendationResponse resolve(
            RecommendationDraft draft,
            int requestedCount,
            List<RecommendationSlot> recommendationSlots,
            String userRequest,
            List<String> avoidSongTitles
    ) {
        List<RecommendationSlot> slots = RecommendationSlots.normalize(recommendationSlots);
        AgentRunContext.runId().ifPresent(runId -> tracePublisher.publishRecommendationDone(runId, draft.candidates().size()));
        AgentRunContext.runId().ifPresent(tracePublisher::publishRecommendationResolveRunning);
        AgentRunContext.runId().ifPresent(runId -> tracePublisher.publishRecommendationResolveToolStart(runId, draft.candidates().size()));
        RecommendationResult result = songResolver.resolve(draft.candidates(), requestedCount);
        if (result.resolved().isEmpty()) {
            result = fallbackResult(result, requestedCount, slots, userRequest, avoidSongTitles);
        }
        List<String> resolvedTitles = result.resolved().stream()
                .map(item -> titleWithArtist(item.song().title(), item.song().artists()))
                .toList();
        List<String> unresolvedTitles = result.unresolved().stream()
                .map(this::titleWithArtist)
                .toList();
        AgentRunContext.runId().ifPresent(runId -> tracePublisher.publishRecommendationResolveToolResult(
                runId,
                resolvedTitles,
                unresolvedTitles
        ));
        AgentRunContext.runId().ifPresent(runId -> tracePublisher.publishRecommendationResolveDone(
                runId,
                resolvedTitles,
                unresolvedTitles
        ));

        if (result.resolved().isEmpty()) {
            return new RecommendationResponse(resolveFailureText(), List.of(), result, slots);
        }

        List<Song> songs = result.resolved().stream()
                .map(ResolvedRecommendation::song)
                .toList();
        RecommendationResult finalResult = result;
        AgentRunContext.userId().ifPresent(userId -> recordRecommendationMemory(userId, userRequest, finalResult, songs));
        return new RecommendationResponse(answerText(result, requestedCount, slots), songs, result, slots);
    }

    private RecommendationResult fallbackResult(
            RecommendationResult strictResult,
            int requestedCount,
            List<RecommendationSlot> recommendationSlots,
            String userRequest,
            List<String> avoidSongTitles
    ) {
        List<RecommendationSlot> slots = RecommendationSlots.normalize(recommendationSlots);
        Set<String> avoidedTitles = normalizedTitles(avoidSongTitles);
        Set<String> acceptedSongIds = new LinkedHashSet<>();
        Set<String> acceptedTitles = new LinkedHashSet<>();
        List<ResolvedRecommendation> resolved = new ArrayList<>();

        if (slots.isEmpty()) {
            collectFallbackSongs(
                    resolved,
                    acceptedSongIds,
                    acceptedTitles,
                    avoidedTitles,
                    "",
                    requestedCount(requestedCount),
                    fallbackQueries(null, userRequest)
            );
        } else {
            for (RecommendationSlot slot : slots) {
                collectFallbackSongs(
                        resolved,
                        acceptedSongIds,
                        acceptedTitles,
                        avoidedTitles,
                        slot.slotId(),
                        slot.count(),
                        fallbackQueries(slot, userRequest)
                );
            }
        }

        if (resolved.isEmpty()) {
            return strictResult;
        }
        return new RecommendationResult(
                List.copyOf(resolved),
                strictResult == null || strictResult.unresolved() == null ? List.of() : strictResult.unresolved(),
                "严格候选未匹配，已用音乐源搜索结果兜底匹配 " + resolved.size() + " 首。"
        );
    }

    private void collectFallbackSongs(
            List<ResolvedRecommendation> resolved,
            Set<String> acceptedSongIds,
            Set<String> acceptedTitles,
            Set<String> avoidedTitles,
            String slotId,
            int count,
            List<String> queries
    ) {
        int acceptedForSlot = 0;
        for (String query : queries) {
            if (acceptedForSlot >= count) {
                return;
            }
            for (Song song : songResolver.searchFallbackSongs(query, count)) {
                if (acceptedForSlot >= count) {
                    return;
                }
                if (!acceptFallbackSong(song, acceptedSongIds, acceptedTitles, avoidedTitles)) {
                    continue;
                }
                resolved.add(new ResolvedRecommendation(
                        song,
                        fallbackReason(query),
                        query,
                        slotId
                ));
                acceptedSongIds.add(song.id().strip());
                String normalizedTitle = normalizeTitle(song.title());
                if (!normalizedTitle.isBlank()) {
                    acceptedTitles.add(normalizedTitle);
                }
                acceptedForSlot++;
            }
        }
    }

    private boolean acceptFallbackSong(
            Song song,
            Set<String> acceptedSongIds,
            Set<String> acceptedTitles,
            Set<String> avoidedTitles
    ) {
        if (song == null || song.id() == null || song.id().isBlank() || song.title() == null || song.title().isBlank()) {
            return false;
        }
        String songId = song.id().strip();
        String normalizedTitle = normalizeTitle(song.title());
        return !acceptedSongIds.contains(songId)
                && !normalizedTitle.isBlank()
                && !acceptedTitles.contains(normalizedTitle)
                && !avoidedTitles.contains(normalizedTitle);
    }

    private List<String> fallbackQueries(RecommendationSlot slot, String userRequest) {
        if (slot != null && !shouldUseSlotFallback(slot)) {
            return List.of();
        }
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (slot != null && !slot.target().isBlank()) {
            queries.add(slot.target());
        }
        String request = safe(userRequest);
        if (!request.isBlank()) {
            queries.add(request);
        }
        return List.copyOf(queries);
    }

    private boolean shouldUseSlotFallback(RecommendationSlot slot) {
        String targetType = safe(slot.targetType()).toLowerCase(Locale.ROOT);
        return !"song".equals(targetType);
    }

    private String fallbackReason(String query) {
        String target = safe(query);
        if (target.isBlank()) {
            return "根据你的当前请求，从音乐源搜索结果中选择的可播放歌曲。";
        }
        return "根据「" + target + "」这个方向，从音乐源搜索结果中选择的可播放歌曲。";
    }

    private void recordRecommendationMemory(String userId, String userRequest, RecommendationResult result, List<Song> songs) {
        if (taskMemoryService == null) {
            return;
        }
        taskMemoryService.recordResultSongs(userId, songs);
        taskMemoryService.recordRecentRecommendations(userId, recentRecommendations(userRequest, result));
    }

    private List<AgentRecentRecommendedSong> recentRecommendations(String userRequest, RecommendationResult result) {
        if (result == null || result.resolved() == null || result.resolved().isEmpty()) {
            return List.of();
        }
        String runId = AgentRunContext.runId().orElse("");
        Instant now = Instant.now();
        return result.resolved().stream()
                .filter(item -> item != null && item.song() != null)
                .map(item -> new AgentRecentRecommendedSong(
                        item.song().id(),
                        item.song().title(),
                        item.song().artists(),
                        item.slotId(),
                        userRequest,
                        item.reason(),
                        runId,
                        "soft_avoid",
                        now
                ))
                .toList();
    }

    private RecommendationResponse draftFailure() {
        RecommendationResult result = new RecommendationResult(
                List.of(),
                List.of(),
                "推荐草稿不可用。"
        );
        AgentRunContext.runId().ifPresent(runId -> tracePublisher.publishRecommendationDone(runId, 0));
        return new RecommendationResponse("""
                这次我没能稳定生成一组可验证的推荐候选，所以先不展示可能不准确的歌曲卡片。你可以换个描述，或者让我再试一次。
                """.strip(), List.of(), result);
    }

    private String resolveFailureText() {
        return """
                我生成了推荐候选，但没有在 QQ 音乐里精确匹配到可播放版本，所以先不展示可能错误的歌曲卡片。你可以换个范围，或者让我再试一次。
                """.strip();
    }

    private String answerText(RecommendationResult result, int requestedCount) {
        return answerText(result, requestedCount, List.of());
    }

    private String answerText(RecommendationResult result, int requestedCount, List<RecommendationSlot> recommendationSlots) {
        StringBuilder builder = new StringBuilder();
        builder.append("我先按你的请求确认推荐方向，再在 QQ 音乐里匹配到这些可播放版本：\n\n");
        for (int index = 0; index < result.resolved().size(); index++) {
            ResolvedRecommendation item = result.resolved().get(index);
            Song song = item.song();
            builder.append(index + 1)
                    .append(". 《")
                    .append(song.title())
                    .append("》- ")
                    .append(song.artists() == null || song.artists().isEmpty() ? "未知歌手" : String.join(" / ", song.artists()))
                    .append('\n')
                    .append(item.reason())
                    .append("\n\n");
        }
        int missing = missingCount(result, requestedCount, recommendationSlots);
        if (missing > 0) {
            builder.append("还有 ")
                    .append(missing)
                    .append(" 首目标没有在 QQ 音乐里稳定匹配到，所以我没有用不确定的搜索结果补卡片。");
        }
        return builder.toString().strip();
    }

    private int missingCount(RecommendationResult result, int requestedCount, List<RecommendationSlot> recommendationSlots) {
        List<RecommendationSlot> slots = RecommendationSlots.normalize(recommendationSlots);
        if (slots.isEmpty()) {
            return Math.max(0, requestedCount(requestedCount) - result.resolved().size());
        }
        int missing = 0;
        for (RecommendationSlot slot : slots) {
            long resolved = result.resolved().stream()
                    .filter(item -> slot.slotId().equals(item.slotId()))
                    .count();
            missing += Math.max(0, slot.count() - (int) resolved);
        }
        return missing;
    }

    private String titleWithArtist(String title, List<String> artists) {
        String artistText = artists == null || artists.isEmpty() ? "" : String.join(" / ", artists);
        if (artistText.isBlank()) {
            return title == null ? "" : title.strip();
        }
        return (title == null ? "" : title.strip()) + " - " + artistText;
    }

    private String titleWithArtist(RecommendationCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        return titleWithArtist(candidate.title(), candidate.artist() == null ? List.of() : List.of(candidate.artist()));
    }

    private Set<String> normalizedTitles(List<String> titles) {
        Set<String> values = new LinkedHashSet<>();
        if (titles == null) {
            return values;
        }
        for (String title : titles) {
            String normalized = normalizeTitle(title);
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private String normalizeTitle(String value) {
        StringBuilder builder = new StringBuilder();
        safe(value).toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }

    private int requestedCount(int count) {
        int value = count <= 0 ? DEFAULT_COUNT : count;
        return Math.max(1, Math.min(MAX_COUNT, value));
    }
}
