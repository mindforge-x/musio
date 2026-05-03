package com.musio.providers.observation;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ObservedMusicProvider implements MusicProvider {
    private final MusicProvider delegate;
    private final ProviderCallObserver observer;

    public ObservedMusicProvider(MusicProvider delegate, ProviderCallObserver observer) {
        this.delegate = delegate;
        this.observer = observer;
    }

    @Override
    public ProviderType type() {
        return delegate.type();
    }

    @Override
    public LoginStartResult startLogin() {
        return observe("start_login", Map.of(), delegate::startLogin, result -> Map.of("state", String.valueOf(result.state())));
    }

    @Override
    public LoginStatus checkLogin(String loginId) {
        return observe("check_login", Map.of("loginId", "[redacted]"), () -> delegate.checkLogin(loginId), result -> Map.of(
                "state", String.valueOf(result.state()),
                "credentialStored", result.credentialStored()
        ));
    }

    @Override
    public UserProfile getProfile(String userId) {
        return observe("get_profile", Map.of("userId", safe(userId)), () -> delegate.getProfile(userId), result -> Map.of(
                "userId", safe(result.id()),
                "displayName", safe(result.displayName())
        ));
    }

    @Override
    public List<Playlist> getPlaylists(String userId) {
        return observe("get_playlists", Map.of("userId", safe(userId)), () -> delegate.getPlaylists(userId), this::playlistListPreview);
    }

    @Override
    public List<Song> getPlaylistSongs(String playlistId) {
        return observe("get_playlist_songs", Map.of("playlistId", safe(playlistId)), () -> delegate.getPlaylistSongs(playlistId), this::songListPreview);
    }

    @Override
    public List<Song> searchSongs(String keyword, int limit) {
        return observe("search_songs", Map.of("keyword", safe(keyword), "limit", limit), () -> delegate.searchSongs(keyword, limit), this::songListPreview);
    }

    @Override
    public SongDetail getSongDetail(String songId) {
        return observe("get_song_detail", Map.of("songId", safe(songId)), () -> delegate.getSongDetail(songId), this::songDetailPreview);
    }

    @Override
    public SongUrl getSongUrl(String songId) {
        return observe("get_song_url", Map.of("songId", safe(songId)), () -> delegate.getSongUrl(songId), result -> Map.of(
                "songId", safe(result.songId()),
                "urlAvailable", result.url() != null && !result.url().isBlank()
        ));
    }

    @Override
    public Lyrics getLyrics(String songId) {
        return observe("get_lyrics", Map.of("songId", safe(songId)), () -> delegate.getLyrics(songId), result -> Map.of(
                "songId", safe(result.songId()),
                "plainTextLength", result.plainText() == null ? 0 : result.plainText().length()
        ));
    }

    @Override
    public List<Comment> getComments(String songId) {
        return observe("get_comments", Map.of("songId", safe(songId)), () -> delegate.getComments(songId), comments -> Map.of(
                "count", comments == null ? 0 : comments.size()
        ));
    }

    private <T> T observe(String operation, Map<String, Object> inputPreview, java.util.function.Supplier<T> action, java.util.function.Function<T, Map<String, Object>> resultPreview) {
        if (observer == null) {
            return action.get();
        }
        return observer.observe(type(), operation, inputPreview, action, resultPreview);
    }

    private Map<String, Object> songListPreview(List<Song> songs) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("count", songs == null ? 0 : songs.size());
        preview.put("titles", songs == null ? List.of() : songs.stream()
                .map(this::songTitle)
                .filter(title -> !title.isBlank())
                .limit(8)
                .toList());
        return preview;
    }

    private Map<String, Object> playlistListPreview(List<Playlist> playlists) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("count", playlists == null ? 0 : playlists.size());
        preview.put("names", playlists == null ? List.of() : playlists.stream()
                .map(Playlist::name)
                .filter(name -> name != null && !name.isBlank())
                .limit(8)
                .toList());
        return preview;
    }

    private Map<String, Object> songPreview(Song song) {
        if (song == null) {
            return Map.of();
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("songId", safe(song.id()));
        preview.put("title", safe(song.title()));
        preview.put("artists", song.artists() == null ? List.of() : song.artists().stream().limit(4).toList());
        return preview;
    }

    private Map<String, Object> songDetailPreview(SongDetail song) {
        if (song == null) {
            return Map.of();
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("songId", safe(song.id()));
        preview.put("title", safe(song.title()));
        preview.put("artists", song.artists() == null ? List.of() : song.artists().stream().limit(4).toList());
        return preview;
    }

    private String songTitle(Song song) {
        if (song == null) {
            return "";
        }
        String artists = song.artists() == null || song.artists().isEmpty() ? "" : " - " + String.join(" / ", song.artists());
        return safe(song.title()) + artists;
    }

    private String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
