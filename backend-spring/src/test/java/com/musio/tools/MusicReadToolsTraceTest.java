package com.musio.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musio.agent.AgentRunContext;
import com.musio.agent.trace.AgentTracePublisher;
import com.musio.events.AgentEventBus;
import com.musio.memory.AgentTaskMemoryService;
import com.musio.model.AgentEvent;
import com.musio.model.AgentTaskMemory;
import com.musio.model.Comment;
import com.musio.model.LoginStartResult;
import com.musio.model.LoginStatus;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.ProviderType;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;
import com.musio.model.UserProfile;
import com.musio.providers.MusicProvider;
import com.musio.providers.MusicProviderGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicReadToolsTraceTest {
    private final AgentEventBus eventBus = new AgentEventBus();

    @AfterEach
    void clearRunContext() {
        AgentRunContext.clear();
    }

    @Test
    void doesNotPublishToolTraceWhenRunTraceIsDisabled() {
        List<AgentEvent> events = subscribe("run-disabled");
        MusicReadTools tools = toolsWith(new FakeProvider());
        AgentRunContext.setRunId("run-disabled");
        AgentRunContext.setTraceEnabled(false);

        tools.searchSongs("周杰伦", 1);

        assertFalse(hasEvent(events, "trace_step"));
        assertTrue(hasEvent(events, "tool_start"));
        assertTrue(hasEvent(events, "tool_result"));
        assertTrue(hasEvent(events, "song_cards"));
    }

    @Test
    void publishesToolTraceWhenRunTraceIsEnabled() {
        List<AgentEvent> events = subscribe("run-enabled");
        MusicReadTools tools = toolsWith(new FakeProvider());
        AgentRunContext.setRunId("run-enabled");
        AgentRunContext.setTraceEnabled(true);

        tools.searchSongs("周杰伦", 1);

        assertTrue(hasEvent(events, "trace_step"));
    }

    @Test
    void publishesErrorTraceWhenToolFails() {
        List<AgentEvent> events = subscribe("run-error");
        MusicReadTools tools = toolsWith(new FailingProvider());
        AgentRunContext.setRunId("run-error");
        AgentRunContext.setTraceEnabled(true);

        tools.searchSongs("周杰伦", 1);

        assertTrue(events.stream().anyMatch(event ->
                "trace_step".equals(event.type()) && "error".equals(event.data().get("status"))
        ));
        assertTrue(hasEvent(events, "tool_result"));
    }

    @Test
    void filtersExcludedSongCardsForContextualAlternativeSearch() {
        List<AgentEvent> events = subscribe("run-alternative");
        MusicReadTools tools = toolsWith(new FakeProvider());
        AgentRunContext.setRunId("run-alternative");
        AgentRunContext.setTraceEnabled(true);

        tools.searchSongsExcludingTitles("周杰伦", 1, List.of("晴天"));

        AgentEvent songCards = events.stream()
                .filter(event -> "song_cards".equals(event.type()))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Song> songs = (List<Song>) songCards.data().get("songs");
        assertEquals(1, songs.size());
        assertEquals("枫", songs.getFirst().title());
    }

    @Test
    void writesSongResultsToTaskMemoryWhenUserContextExists() {
        RecordingTaskMemoryService taskMemory = new RecordingTaskMemoryService();
        MusicReadTools tools = toolsWith(new FakeProvider(), taskMemory);
        AgentRunContext.setRunId("run-memory");
        AgentRunContext.setUserId("local");
        AgentRunContext.setTraceEnabled(true);

        tools.searchSongs("周杰伦", 1);

        assertEquals(List.of("晴天"), taskMemory.songTitles);
    }

    private MusicReadTools toolsWith(MusicProvider provider) {
        return toolsWith(provider, null);
    }

    private MusicReadTools toolsWith(MusicProvider provider, AgentTaskMemoryService taskMemoryService) {
        return new MusicReadTools(
                new MusicProviderGateway(List.of(provider)),
                null,
                eventBus,
                new ObjectMapper(),
                new AgentTracePublisher(eventBus),
                taskMemoryService
        );
    }

    private List<AgentEvent> subscribe(String runId) {
        List<AgentEvent> events = new ArrayList<>();
        eventBus.subscribe(runId, events::add);
        return events;
    }

    private boolean hasEvent(List<AgentEvent> events, String type) {
        return events.stream().anyMatch(event -> type.equals(event.type()));
    }

    private static class FakeProvider implements MusicProvider {
        @Override
        public ProviderType type() {
            return ProviderType.QQMUSIC;
        }

        @Override
        public List<Song> searchSongs(String keyword, int limit) {
            return List.of(
                    new Song("qqmusic:1", ProviderType.QQMUSIC, "晴天", List.of("周杰伦"), "叶惠美", 269, null),
                    new Song("qqmusic:2", ProviderType.QQMUSIC, "枫", List.of("周杰伦"), "十一月的萧邦", 275, null)
            ).stream().limit(limit).toList();
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

    private static final class FailingProvider extends FakeProvider {
        @Override
        public List<Song> searchSongs(String keyword, int limit) {
            throw new IllegalStateException("provider unavailable");
        }
    }

    private static final class RecordingTaskMemoryService extends AgentTaskMemoryService {
        private List<String> songTitles = List.of();

        private RecordingTaskMemoryService() {
            super(null);
        }

        @Override
        public AgentTaskMemory recordResultSongs(String userId, List<Song> songs) {
            songTitles = songs.stream().map(Song::title).toList();
            return AgentTaskMemory.empty(userId);
        }
    }
}
