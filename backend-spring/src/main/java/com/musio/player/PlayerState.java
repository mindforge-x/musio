package com.musio.player;

import com.musio.model.Song;

import java.util.List;

public record PlayerState(
        Song currentSong,
        List<Song> queue,
        boolean paused,
        int positionSeconds,
        Integer durationSeconds,
        PlaybackMode playbackMode,
        String lyricLine
) {
    public enum PlaybackMode {
        SEQUENTIAL,
        REPEAT_ONE,
        REPEAT_ALL,
        SHUFFLE
    }
}
