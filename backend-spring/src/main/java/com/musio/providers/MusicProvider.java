package com.musio.providers;

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

import java.util.List;

public interface MusicProvider {
    ProviderType type();

    LoginStartResult startLogin();

    LoginStatus checkLogin(String loginId);

    UserProfile getProfile(String userId);

    List<Playlist> getPlaylists(String userId);

    List<Song> getPlaylistSongs(String playlistId);

    List<Song> searchSongs(String keyword, int limit);

    SongDetail getSongDetail(String songId);

    SongUrl getSongUrl(String songId);

    Lyrics getLyrics(String songId);

    List<Comment> getComments(String songId);
}
