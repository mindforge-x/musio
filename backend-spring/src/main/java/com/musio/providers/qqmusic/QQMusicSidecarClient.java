package com.musio.providers.qqmusic;

import com.musio.model.Comment;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.ProviderType;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;
import com.musio.model.UserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QQMusicSidecarClient {
    private final String baseUrl;

    public QQMusicSidecarClient(@Value("${musio.providers.qqmusic.sidecar-base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public UserProfile profile() {
        return new UserProfile("local", ProviderType.QQMUSIC, "Local QQ Music user", null);
    }

    public List<Playlist> playlists() {
        return List.of();
    }

    public List<Song> playlistSongs(String playlistId) {
        return List.of();
    }

    public List<Song> search(String keyword, int limit) {
        return List.of();
    }

    public SongDetail song(String songId) {
        return new SongDetail(songId, ProviderType.QQMUSIC, "", List.of(), "", null, null, baseUrl);
    }

    public SongUrl songUrl(String songId) {
        return new SongUrl(songId, ProviderType.QQMUSIC, null, null);
    }

    public Lyrics lyrics(String songId) {
        return new Lyrics(songId, ProviderType.QQMUSIC, "", "");
    }

    public List<Comment> comments(String songId) {
        return List.of();
    }
}
