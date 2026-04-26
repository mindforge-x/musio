package com.musio.providers.qqmusic;

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
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QQMusicProvider implements MusicProvider {
    private final QQMusicAuthService authService;
    private final QQMusicSidecarClient sidecarClient;

    public QQMusicProvider(QQMusicAuthService authService, QQMusicSidecarClient sidecarClient) {
        this.authService = authService;
        this.sidecarClient = sidecarClient;
    }

    @Override
    public ProviderType type() {
        return ProviderType.QQMUSIC;
    }

    @Override
    public LoginStartResult startLogin() {
        return authService.startLogin();
    }

    @Override
    public LoginStatus checkLogin(String loginId) {
        return authService.checkLogin(loginId);
    }

    @Override
    public UserProfile getProfile(String userId) {
        return sidecarClient.profile();
    }

    @Override
    public List<Playlist> getPlaylists(String userId) {
        return sidecarClient.playlists();
    }

    @Override
    public List<Song> getPlaylistSongs(String playlistId) {
        return sidecarClient.playlistSongs(playlistId);
    }

    @Override
    public List<Song> searchSongs(String keyword, int limit) {
        return sidecarClient.search(keyword, limit);
    }

    @Override
    public SongDetail getSongDetail(String songId) {
        return sidecarClient.song(songId);
    }

    @Override
    public SongUrl getSongUrl(String songId) {
        return sidecarClient.songUrl(songId);
    }

    @Override
    public Lyrics getLyrics(String songId) {
        return sidecarClient.lyrics(songId);
    }

    @Override
    public List<Comment> getComments(String songId) {
        return sidecarClient.comments(songId);
    }
}
