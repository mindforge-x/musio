package com.musio.agent;

import com.musio.agent.trace.AgentTracePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentTaskMemory;
import com.musio.model.ProviderType;
import com.musio.model.Song;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskContextResolverTest {
    private final AgentTaskContextResolver resolver = new AgentTaskContextResolver(
            null,
            new ObjectMapper(),
            new AgentTracePublisher(new AgentEventBus())
    );

    @Test
    void parsesModelAgentFollowUpAsEffectiveRequest() {
        AgentTaskContext context = resolver.parseModelResponse("再试试", """
                {"mode":"agent","followUp":true,"effectiveRequest":"给我推荐 10 首适合深夜听的歌","searchKeyword":"深夜听","searchLimit":10,"avoidSongTitles":["晴天"],"confidence":0.91}
                """).orElseThrow();

        assertEquals("给我推荐 10 首适合深夜听的歌", context.planningMessage());
        assertEquals("深夜听", context.searchKeyword());
        assertEquals(10, context.searchLimit());
        assertEquals("recommend", context.taskType());
        assertEquals(List.of("晴天"), context.avoidSongTitles());
        assertTrue(context.agentTask());
        assertTrue(context.followUp());
        assertTrue(context.recommendationPreludeAllowed());
        assertTrue(context.promptContext().contains("上下文延续请求"));
        assertTrue(context.promptContext().contains("晴天"));
    }

    @Test
    void parsesModelChatAsDirectTask() {
        AgentTaskContext context = resolver.parseModelResponse("谢谢", """
                {"mode":"chat","followUp":false,"effectiveRequest":"","searchKeyword":"","searchLimit":0,"avoidSongTitles":[],"confidence":0.88}
                """).orElseThrow();

        assertEquals("谢谢", context.planningMessage());
        assertEquals("chat", context.taskType());
        assertFalse(context.agentTask());
        assertFalse(context.followUp());
    }

    @Test
    void rejectsLowConfidenceModelDecision() {
        assertTrue(resolver.parseModelResponse("再试试", """
                {"mode":"agent","followUp":true,"effectiveRequest":"给我推荐 10 首适合深夜听的歌","searchKeyword":"深夜听","searchLimit":10,"avoidSongTitles":[],"confidence":0.31}
                """).isEmpty());
    }

    @Test
    void rejectsAgentDecisionWithoutTraceableEffectiveRequest() {
        assertTrue(resolver.parseModelResponse("再试试", """
                {"mode":"agent","followUp":true,"effectiveRequest":"继续刚才那个","searchKeyword":"周杰伦","searchLimit":1,"avoidSongTitles":["晴天"],"confidence":0.90}
                """).isEmpty());
    }

    @Test
    void dropsSearchKeywordWhenItContainsAvoidedTitle() {
        AgentTaskContext context = resolver.parseModelResponse("换一首", """
                {"mode":"agent","followUp":true,"effectiveRequest":"搜索周杰伦的一首歌曲","searchKeyword":"周杰伦 不同于 晴天","searchLimit":1,"avoidSongTitles":["晴天"],"confidence":0.91}
                """).orElseThrow();

        assertEquals("", context.searchKeyword());
        assertEquals(1, context.searchLimit());
        assertEquals("search", context.taskType());
        assertEquals(List.of("晴天"), context.avoidSongTitles());
        assertTrue(context.searchPreludeAllowed());
    }

    @Test
    void parsesCommentFollowUpWithTargetSongWithoutSearchPrelude() {
        AgentTaskContext context = resolver.parseModelResponse("上一首歌有没有什么背景故事，或者最感人的评论可以分享", """
                {"mode":"agent","taskType":"comments","followUp":true,"effectiveRequest":"读取并总结林俊杰《Always Online》的热门评论和歌曲背景","searchKeyword":"","searchLimit":0,"targetSongId":"qqmusic:001ABC","targetSongTitle":"Always Online","avoidSongTitles":[],"confidence":0.91}
                """).orElseThrow();

        assertEquals("comments", context.taskType());
        assertEquals("qqmusic:001ABC", context.targetSongId());
        assertEquals("Always Online", context.targetSongTitle());
        assertEquals("", context.searchKeyword());
        assertFalse(context.searchPreludeAllowed());
        assertFalse(context.recommendationPreludeAllowed());
        assertTrue(context.promptContext().contains("目标歌曲 ID 是：qqmusic:001ABC"));
    }

    @Test
    void fallsBackToDirectWhenThereIsNoHistory() {
        AgentTaskContext context = resolver.resolve(null, "再试试", List.of(), AgentTaskMemory.empty("local"));

        assertEquals("再试试", context.planningMessage());
        assertFalse(context.agentTask());
    }

    @Test
    void fallsBackToHeuristicAgentForFirstTurnMusicTaskWhenModelUnavailable() {
        AgentTaskContext context = resolver.resolve(null, "搜索周杰伦的一首歌", List.of(), AgentTaskMemory.empty("local"));

        assertEquals("搜索周杰伦的一首歌", context.planningMessage());
        assertEquals("search", context.taskType());
        assertTrue(context.agentTask());
        assertTrue(context.toolEvidenceExpected());
    }

    @Test
    void buildsTaskMemoryPreviewForResolverPrompt() {
        AgentTaskMemory memory = new AgentTaskMemory(
                "local",
                "music-agent-task",
                "搜索周杰伦的一首歌曲",
                "周杰伦",
                1,
                List.of(),
                List.of("晴天"),
                List.of("晴天"),
                List.of(),
                java.time.Instant.now()
        );

        String preview = resolver.taskMemoryPreview(memory);

        assertTrue(preview.contains("lastSearchKeyword: 周杰伦"));
        assertTrue(preview.contains("lastResultSongTitles: 晴天"));
    }

    @Test
    void buildsTaskMemoryPreviewWithSongRefsForToolFollowUp() {
        AgentTaskMemory memory = new AgentTaskMemory(
                "local",
                "music-agent-task",
                "搜索林俊杰的一首歌",
                "林俊杰",
                1,
                List.of(new Song("qqmusic:001ABC", ProviderType.QQMUSIC, "Always Online", List.of("林俊杰"), "JJ陆", 303, null)),
                List.of("Always Online"),
                List.of(),
                List.of(),
                java.time.Instant.now()
        );

        String preview = resolver.taskMemoryPreview(memory);

        assertTrue(preview.contains("lastResultSongRefs: Always Online | 林俊杰 | id=qqmusic:001ABC"));
    }
}
