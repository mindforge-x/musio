package com.musio.providers;

import com.musio.agent.AgentRunContext;
import com.musio.events.AgentEventBus;
import com.musio.model.AgentEvent;
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
import com.musio.providers.observation.ProviderCallObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicProviderGatewayObservationTest {
    private final AgentEventBus eventBus = new AgentEventBus();

    @AfterEach
    void clearRunContext() {
        AgentRunContext.clear();
    }

    @Test
    void publishesProviderCallEventsWhenRunContextExists() {
        List<AgentEvent> events = subscribe("run-provider");
        MusicProviderGateway gateway = new MusicProviderGateway(
                List.of(new FakeProvider()),
                new ProviderCallObserver(eventBus)
        );
        AgentRunContext.setRunId("run-provider");

        List<Song> songs = gateway.defaultProvider().searchSongs("安静 周杰伦", 10);

        assertEquals(1, songs.size());
        AgentEvent start = events.stream()
                .filter(event -> "tool_start".equals(event.type()))
                .filter(event -> "qqmusic.search_songs".equals(event.data().get("tool")))
                .findFirst()
                .orElseThrow();
        assertEquals("provider_call", start.data().get("layer"));
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) start.data().get("input");
        assertEquals("安静 周杰伦", input.get("keyword"));
        assertEquals(10, input.get("limit"));

        AgentEvent result = events.stream()
                .filter(event -> "tool_result".equals(event.type()))
                .filter(event -> "qqmusic.search_songs".equals(event.data().get("tool")))
                .findFirst()
                .orElseThrow();
        assertEquals("done", result.data().get("status"));
        assertTrue(((String) result.data().get("summary")).contains("returned 1"));
    }

    @Test
    void doesNotPublishProviderCallEventsWithoutRunContext() {
        List<AgentEvent> events = subscribe("run-provider");
        MusicProviderGateway gateway = new MusicProviderGateway(
                List.of(new FakeProvider()),
                new ProviderCallObserver(eventBus)
        );

        gateway.defaultProvider().searchSongs("安静 周杰伦", 10);

        assertTrue(events.isEmpty());
    }

    @Test
    void publishesProviderCallErrorAndRethrows() {
        List<AgentEvent> events = subscribe("run-error");
        MusicProviderGateway gateway = new MusicProviderGateway(
                List.of(new FailingProvider()),
                new ProviderCallObserver(eventBus)
        );
        AgentRunContext.setRunId("run-error");

        assertThrows(IllegalStateException.class, () -> gateway.defaultProvider().searchSongs("安静 周杰伦", 10));

        AgentEvent result = events.stream()
                .filter(event -> "tool_result".equals(event.type()))
                .filter(event -> "qqmusic.search_songs".equals(event.data().get("tool")))
                .findFirst()
                .orElseThrow();
        assertEquals("error", result.data().get("status"));
        assertTrue(((String) result.data().get("summary")).contains("provider unavailable"));
    }

    private List<AgentEvent> subscribe(String runId) {
        List<AgentEvent> events = new ArrayList<>();
        eventBus.subscribe(runId, events::add);
        return events;
    }

    private static class FakeProvider implements MusicProvider {
        @Override
        public ProviderType type() {
            return ProviderType.QQMUSIC;
        }

        @Override
        public List<Song> searchSongs(String keyword, int limit) {
            return List.of(new Song("qqmusic:quiet", ProviderType.QQMUSIC, "安静", List.of("周杰伦"), "范特西", 334, null));
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
}
