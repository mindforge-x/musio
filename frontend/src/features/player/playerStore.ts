import { useState } from "react";
import { PlaybackMode, PlayerState, Song } from "../../shared/types";

const PLAYBACK_MODES: PlaybackMode[] = ["SEQUENTIAL", "REPEAT_ONE", "REPEAT_ALL", "SHUFFLE"];

const initialPlayerState: PlayerState = {
  currentSong: null,
  queue: [],
  paused: true,
  positionSeconds: 0,
  durationSeconds: null,
  playbackMode: "SEQUENTIAL",
  lyricLine: "暂无歌词"
};

export function usePlayerStore() {
  const [state, setState] = useState<PlayerState>(initialPlayerState);

  return {
    state,
    playSong: (song: Song) => {
      setState((current) => ({
        ...current,
        currentSong: song,
        queue: current.queue.some((item) => item.id === song.id) ? current.queue : [...current.queue, song],
        paused: false,
        positionSeconds: 0,
        durationSeconds: song.durationSeconds ?? null,
        lyricLine: "歌词会显示在这里"
      }));
    },
    togglePaused: () => {
      setState((current) => ({ ...current, paused: !current.paused }));
    },
    nextMode: () => {
      setState((current) => {
        const index = PLAYBACK_MODES.indexOf(current.playbackMode);
        return { ...current, playbackMode: PLAYBACK_MODES[(index + 1) % PLAYBACK_MODES.length] };
      });
    }
  };
}
