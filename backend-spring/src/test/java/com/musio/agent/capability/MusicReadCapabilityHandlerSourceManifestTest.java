package com.musio.agent.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.agent.AgentRunContext;
import com.musio.events.AgentEventBus;
import com.musio.model.Comment;
import com.musio.model.LoginStartResult;
import com.musio.model.LoginStatus;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.ProviderType;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;
import com.musio.model.SourceContext;
import com.musio.model.UserProfile;
import com.musio.providers.MusicProvider;
import com.musio.providers.MusicProviderGateway;
import com.musio.providers.MusicSourceProvider;
import com.musio.providers.SourceCapability;
import com.musio.providers.SourceToolCall;
import com.musio.tools.MusicReadTools;
import com.musio.agent.trace.AgentTracePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicReadCapabilityHandlerSourceManifestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearRunContext() {
        AgentRunContext.clear();
    }

    @Test
    void exposesEnabledSourceCapabilitiesAndFiltersDisabledTools() {
        MusicReadCapabilityHandler handler = handler(new DynamicProvider());
        AgentRunContext.setSourceContext(new SourceContext(List.of("qqmusic"), "qqmusic", "local"));

        List<String> names = handler.capabilities().stream().map(AgentCapability::name).toList();

        assertTrue(names.contains("get_similar_songs"));
        assertTrue(names.contains("get_user_music_profile"));
        assertFalse(names.contains("disabled_tool"));
        assertTrue(handler.supports("get_similar_songs"));
        assertFalse(handler.supports("disabled_tool"));
    }

    @Test
    void validatesRequiredArgumentsFromSourceCapability() {
        MusicReadCapabilityHandler handler = handler(new DynamicProvider());
        AgentRunContext.setSourceContext(new SourceContext(List.of("qqmusic"), "qqmusic", "local"));

        assertFalse(handler.validateArguments("get_similar_songs", Map.of(), AgentCapabilityArgumentContext.stepPlanner(1)).valid());
        assertTrue(handler.validateArguments("get_similar_songs", Map.of("songId", "qqmusic:1"), AgentCapabilityArgumentContext.stepPlanner(1)).valid());
    }

    @Test
    void executesDynamicSourceCapabilityThroughGenericToolExecutor() throws Exception {
        DynamicProvider provider = new DynamicProvider();
        MusicReadCapabilityHandler handler = handler(provider);
        AgentRunContext.setSourceContext(new SourceContext(List.of("qqmusic"), "qqmusic", "local"));

        String resultJson = handler.execute(null, "get_similar_songs", Map.of("songId", "qqmusic:1", "limit", 1))
                .orElseThrow();

        assertEquals("get_similar_songs", provider.lastToolName);
        var root = objectMapper.readTree(resultJson);
        assertTrue(root.path("success").asBoolean());
        assertEquals("songs", root.path("resultType").asText());
        assertEquals("相似歌曲", root.path("songs").get(0).path("title").asText());
    }

    private MusicReadCapabilityHandler handler(MusicProvider provider) {
        AgentEventBus eventBus = new AgentEventBus();
        MusicReadTools tools = new MusicReadTools(
                new MusicProviderGateway(List.of(provider)),
                null,
                eventBus,
                new ObjectMapper(),
                new AgentTracePublisher(eventBus),
                null
        );
        return new MusicReadCapabilityHandler(tools);
    }

    private static final class DynamicProvider implements MusicProvider, MusicSourceProvider {
        private String lastToolName;

        @Override
        public String sourceId() {
            return ProviderType.QQMUSIC.sourceId();
        }

        @Override
        public List<SourceCapability> capabilities(SourceContext context) {
            return List.of(
                    new SourceCapability(
                            "get_similar_songs",
                            CapabilityEffect.READ,
                            "根据一首歌获取相似歌曲",
                            Map.of("songId", "string", "limit", "number"),
                            Set.of("songId"),
                            true,
                            "",
                            "songs"
                    ),
                    new SourceCapability(
                            "disabled_tool",
                            CapabilityEffect.READ,
                            "disabled",
                            Map.of(),
                            Set.of(),
                            false,
                            "disabled in test",
                            "generic"
                    )
            );
        }

        @Override
        public Map<String, Object> execute(SourceToolCall call, SourceContext context) {
            lastToolName = call.toolName();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("sourceId", sourceId());
            result.put("toolName", call.toolName());
            result.put("resultType", "songs");
            result.put("count", 1);
            result.put("songs", List.of(new Song("qqmusic:similar", ProviderType.QQMUSIC, "相似歌曲", List.of("歌手"), "专辑", 180, null)));
            return result;
        }

        @Override
        public ProviderType type() {
            return ProviderType.QQMUSIC;
        }

        @Override
        public LoginStartResult startLogin() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoginStatus checkLogin(String loginId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile getProfile(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Playlist> getPlaylists(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Song> getPlaylistSongs(String playlistId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Song> searchSongs(String keyword, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SongDetail getSongDetail(String songId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SongUrl getSongUrl(String songId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Lyrics getLyrics(String songId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Comment> getComments(String songId) {
            throw new UnsupportedOperationException();
        }
    }
}
