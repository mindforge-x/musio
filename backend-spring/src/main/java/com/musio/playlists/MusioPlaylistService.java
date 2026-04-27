package com.musio.playlists;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MusioPlaylistService {
    private final Map<String, MusioPlaylist> playlists = new ConcurrentHashMap<>();

    public MusioPlaylistService() {
        Instant now = Instant.now();
        MusioPlaylist defaultPlaylist = new MusioPlaylist(
                "default",
                "Musio Queue",
                "Cross-source songs saved from Agent results.",
                List.of(),
                now,
                now
        );
        playlists.put(defaultPlaylist.id(), defaultPlaylist);
    }

    public List<MusioPlaylist> list() {
        return playlists.values().stream().toList();
    }

    public MusioPlaylist get(String playlistId) {
        MusioPlaylist playlist = playlists.get(playlistId);
        if (playlist == null) {
            throw new IllegalArgumentException("Musio playlist not found: " + playlistId);
        }
        return playlist;
    }
}
