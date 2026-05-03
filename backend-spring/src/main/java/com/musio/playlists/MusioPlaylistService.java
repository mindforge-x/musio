package com.musio.playlists;

import com.musio.model.Song;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public synchronized MusioPlaylist addSong(String playlistId, Song song) {
        MusioPlaylist playlist = get(playlistId);
        if (song == null || song.id() == null || song.id().isBlank()) {
            throw new IllegalArgumentException("Song id is required.");
        }
        boolean exists = playlist.items().stream()
                .anyMatch(item -> song.id().equals(item.providerTrackId()));
        if (exists) {
            return playlist;
        }

        Instant now = Instant.now();
        List<MusioPlaylistItem> items = new ArrayList<>(playlist.items());
        items.add(new MusioPlaylistItem(
                UUID.randomUUID().toString(),
                playlist.id(),
                song.provider(),
                song.id(),
                song.title(),
                song.artists() == null ? List.of() : List.copyOf(song.artists()),
                song.album(),
                song.durationSeconds(),
                song.artworkUrl(),
                null,
                items.size(),
                now
        ));
        MusioPlaylist updated = new MusioPlaylist(
                playlist.id(),
                playlist.name(),
                playlist.description(),
                List.copyOf(items),
                playlist.createdAt(),
                now
        );
        playlists.put(updated.id(), updated);
        return updated;
    }
}
