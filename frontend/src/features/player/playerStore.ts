import { useEffect, useRef, useState } from "react";
import { PlaybackMode, PlayerState, Song } from "../../shared/types";
import { playerClient } from "./playerClient";

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
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const playRequestRef = useRef(0);

  useEffect(() => {
    const audio = new Audio();
    audio.preload = "none";
    audioRef.current = audio;

    function updatePosition() {
      setState((current) => ({
        ...current,
        positionSeconds: Number.isFinite(audio.currentTime) ? Math.floor(audio.currentTime) : current.positionSeconds
      }));
    }

    function updateDuration() {
      setState((current) => ({
        ...current,
        durationSeconds: Number.isFinite(audio.duration) ? Math.floor(audio.duration) : current.durationSeconds
      }));
    }

    function handleEnded() {
      setState((current) => ({
        ...current,
        paused: true,
        positionSeconds: current.durationSeconds ?? current.positionSeconds,
        lyricLine: "播放结束"
      }));
    }

    function handleError() {
      setState((current) => ({
        ...current,
        paused: true,
        lyricLine: "播放失败：音频流暂时不可用"
      }));
    }

    audio.addEventListener("timeupdate", updatePosition);
    audio.addEventListener("loadedmetadata", updateDuration);
    audio.addEventListener("durationchange", updateDuration);
    audio.addEventListener("ended", handleEnded);
    audio.addEventListener("error", handleError);

    return () => {
      audio.pause();
      audio.removeAttribute("src");
      audio.load();
      audio.removeEventListener("timeupdate", updatePosition);
      audio.removeEventListener("loadedmetadata", updateDuration);
      audio.removeEventListener("durationchange", updateDuration);
      audio.removeEventListener("ended", handleEnded);
      audio.removeEventListener("error", handleError);
      audioRef.current = null;
    };
  }, []);

  return {
    state,
    playSong: async (song: Song) => {
      const requestId = playRequestRef.current + 1;
      playRequestRef.current = requestId;
      setState((current) => ({
        ...current,
        currentSong: song,
        queue: current.queue.some((item) => item.id === song.id) ? current.queue : [...current.queue, song],
        paused: false,
        positionSeconds: 0,
        durationSeconds: song.durationSeconds ?? null,
        lyricLine: "正在获取播放地址..."
      }));

      try {
        const songUrl = await playerClient.songUrl(song.id);
        if (playRequestRef.current !== requestId) {
          return;
        }
        if (!songUrl.url) {
          throw new Error("没有拿到可播放地址");
        }

        const audio = audioRef.current;
        if (!audio) {
          throw new Error("播放器尚未初始化");
        }

        audio.pause();
        audio.src = songUrl.url;
        audio.currentTime = 0;
        audio.load();
        setState((current) => ({
          ...current,
          lyricLine: "正在开始播放..."
        }));
        await audio.play();
        if (playRequestRef.current !== requestId) {
          return;
        }
        setState((current) => ({
          ...current,
          paused: false,
          lyricLine: "正在播放"
        }));
      } catch (error) {
        if (playRequestRef.current === requestId) {
          setState((current) => ({
            ...current,
            paused: true,
            lyricLine: `播放失败：${errorMessage(error)}`
          }));
        }
        throw error;
      }
    },
    togglePaused: () => {
      if (!state.currentSong) {
        return;
      }
      const audio = audioRef.current;
      if (!audio) {
        setState((current) => ({ ...current, paused: true, lyricLine: "播放器尚未初始化" }));
        return;
      }
      if (state.paused) {
        audio.play()
          .then(() => setState((current) => ({ ...current, paused: false, lyricLine: "正在播放" })))
          .catch((error) => setState((current) => ({
            ...current,
            paused: true,
            lyricLine: `播放失败：${errorMessage(error)}`
          })));
        return;
      }
      audio.pause();
      setState((current) => ({ ...current, paused: true, lyricLine: "已暂停" }));
    },
    nextMode: () => {
      setState((current) => {
        const index = PLAYBACK_MODES.indexOf(current.playbackMode);
        return { ...current, playbackMode: PLAYBACK_MODES[(index + 1) % PLAYBACK_MODES.length] };
      });
    }
  };
}

function errorMessage(error: unknown) {
  return error instanceof Error && error.message ? error.message : "未知错误";
}
