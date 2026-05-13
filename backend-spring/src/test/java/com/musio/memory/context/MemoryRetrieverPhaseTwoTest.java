package com.musio.memory.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.agent.AgentGoal;
import com.musio.agent.AgentRequiredOutcome;
import com.musio.memory.BehaviorEvent;
import com.musio.memory.BehaviorEventStore;
import com.musio.memory.BehaviorSummaryService;
import com.musio.memory.ConversationSummary;
import com.musio.memory.ConversationSummaryStore;
import com.musio.memory.MusicCacheEntry;
import com.musio.memory.MusicCacheStore;
import com.musio.memory.PreferenceAggregator;
import com.musio.memory.PreferenceCandidate;
import com.musio.memory.PreferenceStore;
import com.musio.memory.SQLiteMemoryDatabase;
import com.musio.model.AgentTaskMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRetrieverPhaseTwoTest {
    @TempDir
    Path tempDir;

    @Test
    void retrievesBehaviorSummaryPreferenceItemsMusicCacheAndConversationSummary() {
        SQLiteMemoryDatabase database = new SQLiteMemoryDatabase(tempDir.resolve("memory.sqlite"));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BehaviorEventStore behaviorStore = new BehaviorEventStore(database, objectMapper);
        PreferenceStore preferenceStore = new PreferenceStore(database);
        MusicCacheStore musicCacheStore = new MusicCacheStore(database);
        ConversationSummaryStore conversationSummaryStore = new ConversationSummaryStore(database, objectMapper);
        Instant now = Instant.parse("2026-05-13T10:00:00Z");

        behaviorStore.append(new BehaviorEvent(
                "",
                "local",
                "recommendation_shown",
                "recommend_songs",
                "qqmusic:1",
                "安静",
                List.of("周杰伦"),
                "推荐展示",
                Map.of("request", "晚上写代码推荐"),
                0.9,
                now
        ));
        preferenceStore.addCandidate(new PreferenceCandidate(
                "",
                "local",
                "negative",
                "too_noisy",
                "不想听太吵的歌",
                0.18,
                "用户说别太吵",
                "session_feedback",
                now
        ));
        new PreferenceAggregator(preferenceStore).aggregate("local", now.plusSeconds(1));
        musicCacheStore.upsert(new MusicCacheEntry(
                "",
                "local",
                "comments",
                "qqmusic:1",
                "安静",
                "评论摘要：很多人说这首歌安静、治愈，适合夜间专注。",
                "热门评论缓存",
                now
        ));
        conversationSummaryStore.upsert(new ConversationSummary(
                "",
                "local",
                "用户之前让 Musio 推荐晚上写代码适合听的安静歌曲。",
                List.of("写代码", "晚上", "推荐"),
                now
        ));

        MemoryRetriever retriever = new MemoryRetriever(
                null,
                new BehaviorSummaryService(behaviorStore, preferenceStore),
                musicCacheStore,
                conversationSummaryStore,
                preferenceStore
        );
        MemoryRouteRequest request = new MemoryRouteRequest(
                "local",
                "推荐一首晚上写代码听的，别太吵",
                "recommend",
                "new_task",
                "推荐一首晚上写代码听的，别太吵",
                goal(),
                AgentTaskMemory.empty("local"),
                List.of()
        );
        MemoryReadPlan plan = new MemoryReadPlan(List.of(
                new MemoryReadItem(MemoryType.PROFILE_MEMORY, List.of("avoid"), "", "profile", 80, 5, "读取负向偏好"),
                new MemoryReadItem(MemoryType.BEHAVIOR_SUMMARY, List.of("last7DaysSummary", "negativeSignals", "sceneSignals"), "", "last_7_days", 70, 5, "读取近期行为"),
                new MemoryReadItem(MemoryType.MUSIC_CACHE, List.of("comments"), "治愈", "session", 65, 3, "读取评论缓存"),
                new MemoryReadItem(MemoryType.CONVERSATION_SUMMARY, List.of("dialogueSummary"), "写代码", "session", 55, 2, "读取历史摘要")
        ), 1200);

        MemoryContextPackage context = new MemoryCompressor().compress(retriever.retrieve(request, plan), 1200);

        assertTrue(context.promptText().contains("负向偏好候选聚合"));
        assertTrue(context.promptText().contains("近期行为摘要"));
        assertTrue(context.promptText().contains("音乐内容缓存"));
        assertTrue(context.promptText().contains("历史会话摘要"));
        assertTrue(context.promptText().contains("不想听太吵"));
        assertTrue(context.promptText().contains("治愈"));
    }

    private AgentGoal goal() {
        return new AgentGoal(
                "推荐一首晚上写代码听的，别太吵",
                "推荐一首晚上写代码听的，别太吵",
                "recommend",
                "new_task",
                true,
                true,
                false,
                false,
                1,
                List.of(),
                List.of(AgentRequiredOutcome.RECOMMENDATION)
        );
    }
}
