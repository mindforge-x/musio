package com.musio.agent.recommendation;

import com.musio.agent.AgentRunContext;
import com.musio.agent.trace.AgentTracePublisher;
import com.musio.config.MusioConfig;
import com.musio.memory.AgentTaskMemoryService;
import com.musio.model.AgentTaskMemory;
import com.musio.model.Song;
import org.springframework.stereotype.Component;

import java.util.List;

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
                .map(draft -> resolve(draft, count))
                .orElseGet(this::draftFailure);
    }

    private RecommendationResponse resolve(RecommendationDraft draft, int requestedCount) {
        AgentRunContext.runId().ifPresent(runId -> tracePublisher.publishRecommendationDone(runId, draft.candidates().size()));
        AgentRunContext.runId().ifPresent(tracePublisher::publishRecommendationResolveRunning);
        AgentRunContext.runId().ifPresent(runId -> tracePublisher.publishRecommendationResolveToolStart(runId, draft.candidates().size()));
        RecommendationResult result = songResolver.resolve(draft.candidates(), requestedCount);
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
            return new RecommendationResponse(resolveFailureText(), List.of(), result);
        }

        List<Song> songs = result.resolved().stream()
                .map(ResolvedRecommendation::song)
                .toList();
        AgentRunContext.userId().ifPresent(userId -> taskMemoryService.recordResultSongs(userId, songs));
        return new RecommendationResponse(answerText(result, requestedCount), songs, result);
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
        StringBuilder builder = new StringBuilder();
        builder.append("我先按你的请求和音乐画像定了候选，再在 QQ 音乐里精确匹配到这些可播放版本：\n\n");
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
        int missing = Math.max(0, requestedCount(requestedCount) - result.resolved().size());
        if (missing > 0) {
            builder.append("还有 ")
                    .append(missing)
                    .append(" 首候选没有在 QQ 音乐里精确匹配到，所以我没有用不确定的搜索结果补卡片。");
        }
        return builder.toString().strip();
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

    private int requestedCount(int count) {
        int value = count <= 0 ? DEFAULT_COUNT : count;
        return Math.max(1, Math.min(MAX_COUNT, value));
    }
}
