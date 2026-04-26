package com.musio.api;

import com.musio.model.Comment;
import com.musio.model.Lyrics;
import com.musio.model.Playlist;
import com.musio.model.Song;
import com.musio.model.SongDetail;
import com.musio.model.SongUrl;
import com.musio.model.UserProfile;
import com.musio.providers.MusicProviderGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/music")
public class MusicController {
    private final MusicProviderGateway providerGateway;

    public MusicController(MusicProviderGateway providerGateway) {
        this.providerGateway = providerGateway;
    }

    @GetMapping("/profile")
    public UserProfile profile() {
        return providerGateway.defaultProvider().getProfile("local");
    }

    @GetMapping("/playlists")
    public List<Playlist> playlists() {
        return providerGateway.defaultProvider().getPlaylists("local");
    }

    @GetMapping("/playlists/{playlistId}/songs")
    public List<Song> playlistSongs(@PathVariable String playlistId) {
        return providerGateway.defaultProvider().getPlaylistSongs(playlistId);
    }

    @GetMapping("/search")
    public List<Song> search(@RequestParam String keyword, @RequestParam(defaultValue = "10") int limit) {
        return providerGateway.defaultProvider().searchSongs(keyword, limit);
    }

    @GetMapping("/songs/{songId}")
    public SongDetail song(@PathVariable String songId) {
        return providerGateway.defaultProvider().getSongDetail(songId);
    }

    @GetMapping("/songs/{songId}/url")
    public SongUrl songUrl(@PathVariable String songId) {
        return providerGateway.defaultProvider().getSongUrl(songId);
    }

    @GetMapping("/songs/{songId}/lyrics")
    public Lyrics lyrics(@PathVariable String songId) {
        return providerGateway.defaultProvider().getLyrics(songId);
    }

    @GetMapping("/songs/{songId}/comments")
    public List<Comment> comments(@PathVariable String songId) {
        return providerGateway.defaultProvider().getComments(songId);
    }
}
