import { useEffect, useRef, useState } from "react";
import { PlaybackMode, PlayerState, Song } from "../../shared/types";
import { playerClient } from "./playerClient";

const PLAYBACK_MODES: PlaybackMode[] = ["SEQUENTIAL", "REPEAT_ONE", "REPEAT_ALL", "SHUFFLE"];
const SPECTRUM_BAR_COUNT = 56;
const SPECTRUM_MIN_HZ = 32;
const SPECTRUM_MAX_HZ = 16_000;
const IDLE_SPECTRUM_LEVELS = Array.from({ length: SPECTRUM_BAR_COUNT }, () => 6);
const QUIET_SPECTRUM_LEVELS = Array.from({ length: SPECTRUM_BAR_COUNT }, () => 6);

const initialPlayerState: PlayerState = {
  currentSong: null,
  queue: [],
  currentIndex: -1,
  paused: true,
  positionSeconds: 0,
  durationSeconds: null,
  playbackMode: "SEQUENTIAL",
  lyricLine: "[NO TRACK]",
  lyricsText: "",
  spectrumLevels: IDLE_SPECTRUM_LEVELS
};

type WebAudioWindow = Window & typeof globalThis & {
  webkitAudioContext?: typeof AudioContext;
};

type SyncedLyricLine = {
  timeSeconds: number;
  text: string;
};

export function usePlayerStore() {
  const [state, setState] = useState<PlayerState>(initialPlayerState);
  const stateRef = useRef<PlayerState>(initialPlayerState);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const playRequestRef = useRef(0);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const sourceNodeRef = useRef<MediaElementAudioSourceNode | null>(null);
  const spectrumDataRef = useRef<Uint8Array<ArrayBuffer> | null>(null);
  const spectrumFrameRef = useRef<number | null>(null);
  const spectrumLastUpdateRef = useRef(0);
  const syncedLyricsRef = useRef<SyncedLyricLine[]>([]);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  useEffect(() => {
    const audio = new Audio();
    audio.preload = "none";
    audioRef.current = audio;

    function updatePosition() {
      const positionSeconds = Number.isFinite(audio.currentTime) ? audio.currentTime : null;
      const lyricLine = positionSeconds === null ? null : lyricLineAt(positionSeconds, syncedLyricsRef.current);
      setState((current) => ({
        ...current,
        positionSeconds: positionSeconds === null ? current.positionSeconds : Math.floor(positionSeconds),
        lyricLine: lyricLine ?? current.lyricLine
      }));
    }

    function updateDuration() {
      setState((current) => ({
        ...current,
        durationSeconds: Number.isFinite(audio.duration) ? Math.floor(audio.duration) : current.durationSeconds
      }));
    }

    function handleEnded() {
      stopSpectrumMonitor();
      const current = stateRef.current;
      const nextIndex = nextQueueIndex(current);
      if (nextIndex >= 0) {
        void startPlayback(current.queue[nextIndex], current.queue, nextIndex);
        return;
      }
      setState((value) => ({
        ...value,
        paused: true,
        positionSeconds: value.durationSeconds ?? value.positionSeconds,
        lyricLine: "[ENDED]",
        spectrumLevels: IDLE_SPECTRUM_LEVELS
      }));
    }

    function handleError() {
      stopSpectrumMonitor();
      setState((current) => ({
        ...current,
        paused: true,
        lyricLine: "[ERROR: STREAM UNAVAILABLE]",
        spectrumLevels: IDLE_SPECTRUM_LEVELS
      }));
    }

    audio.addEventListener("timeupdate", updatePosition);
    audio.addEventListener("loadedmetadata", updateDuration);
    audio.addEventListener("durationchange", updateDuration);
    audio.addEventListener("ended", handleEnded);
    audio.addEventListener("error", handleError);

    return () => {
      stopSpectrumMonitor(false);
      audio.pause();
      audio.removeAttribute("src");
      audio.load();
      audio.removeEventListener("timeupdate", updatePosition);
      audio.removeEventListener("loadedmetadata", updateDuration);
      audio.removeEventListener("durationchange", updateDuration);
      audio.removeEventListener("ended", handleEnded);
      audio.removeEventListener("error", handleError);
      sourceNodeRef.current?.disconnect();
      analyserRef.current?.disconnect();
      void audioContextRef.current?.close();
      sourceNodeRef.current = null;
      analyserRef.current = null;
      audioContextRef.current = null;
      spectrumDataRef.current = null;
      syncedLyricsRef.current = [];
      audioRef.current = null;
    };
  }, []);

  function ensureAudioAnalyser(audio: HTMLAudioElement) {
    const browserWindow = window as WebAudioWindow;
    const AudioContextConstructor = browserWindow.AudioContext ?? browserWindow.webkitAudioContext;
    if (!AudioContextConstructor) {
      return false;
    }

    try {
      if (!audioContextRef.current) {
        audioContextRef.current = new AudioContextConstructor();
      }
      const audioContext = audioContextRef.current;
      if (!analyserRef.current) {
        const analyser = audioContext.createAnalyser();
        analyser.fftSize = 512;
        analyser.smoothingTimeConstant = 0.68;
        analyserRef.current = analyser;
        spectrumDataRef.current = new Uint8Array(new ArrayBuffer(analyser.frequencyBinCount));
      }
      if (!sourceNodeRef.current) {
        sourceNodeRef.current = audioContext.createMediaElementSource(audio);
        sourceNodeRef.current.connect(analyserRef.current);
        analyserRef.current.connect(audioContext.destination);
      }
      if (audioContext.state === "suspended") {
        void audioContext.resume();
      }
      return true;
    } catch {
      return false;
    }
  }

  function startSpectrumMonitor(audio: HTMLAudioElement) {
    const analyserReady = ensureAudioAnalyser(audio);
    stopSpectrumMonitor(false);
    spectrumLastUpdateRef.current = 0;

    function tick(timestamp: number) {
      const currentAudio = audioRef.current;
      if (!currentAudio || currentAudio.paused || currentAudio.ended) {
        spectrumFrameRef.current = null;
        setState((current) => ({ ...current, spectrumLevels: IDLE_SPECTRUM_LEVELS }));
        return;
      }

      if (timestamp - spectrumLastUpdateRef.current > 70) {
        spectrumLastUpdateRef.current = timestamp;
        const levels = analyserReady ? readAnalyserSpectrum(timestamp) : fallbackSpectrum(timestamp);
        setState((current) => ({ ...current, spectrumLevels: levels }));
      }
      spectrumFrameRef.current = window.requestAnimationFrame(tick);
    }

    spectrumFrameRef.current = window.requestAnimationFrame(tick);
  }

  function stopSpectrumMonitor(reset = true) {
    if (spectrumFrameRef.current !== null) {
      window.cancelAnimationFrame(spectrumFrameRef.current);
      spectrumFrameRef.current = null;
    }
    if (reset) {
      setState((current) => ({ ...current, spectrumLevels: IDLE_SPECTRUM_LEVELS }));
    }
  }

  function readAnalyserSpectrum(timestamp: number) {
    const analyser = analyserRef.current;
    const data = spectrumDataRef.current;
    if (!analyser || !data) {
      return fallbackSpectrum(timestamp);
    }

    analyser.getByteFrequencyData(data);
    let peak = 0;
    for (const value of data) {
      peak = Math.max(peak, value);
    }
    if (peak < 5) {
      return QUIET_SPECTRUM_LEVELS;
    }

    const nyquist = (audioContextRef.current?.sampleRate ?? 48_000) / 2;
    const maxHz = Math.min(SPECTRUM_MAX_HZ, nyquist);
    const binHz = nyquist / data.length;

    return Array.from({ length: SPECTRUM_BAR_COUNT }, (_, index) => {
      const startHz = logarithmicFrequency(index / SPECTRUM_BAR_COUNT, SPECTRUM_MIN_HZ, maxHz);
      const endHz = logarithmicFrequency((index + 1) / SPECTRUM_BAR_COUNT, SPECTRUM_MIN_HZ, maxHz);
      const start = Math.max(1, Math.min(data.length - 1, Math.floor(startHz / binHz)));
      const end = Math.max(start + 1, Math.min(data.length, Math.ceil(endHz / binHz)));
      let sum = 0;
      let localPeak = 0;
      for (let bin = start; bin < end; bin += 1) {
        const value = data[bin] ?? 0;
        sum += value;
        localPeak = Math.max(localPeak, value);
      }
      const average = sum / (end - start);
      const energy = average * 0.68 + localPeak * 0.32;
      const position = index / Math.max(1, SPECTRUM_BAR_COUNT - 1);
      const bandPresence = 0.86 + Math.sin(position * Math.PI) * 0.14;
      const lowEndWeight = index < 7 ? 1.08 : 1;
      const shaped = Math.pow(Math.max(0, energy - 4) / 251, 0.64);
      return clampLevel(4 + shaped * 94 * bandPresence * lowEndWeight);
    });
  }

  async function startPlayback(song: Song, queue: Song[], index: number) {
    const requestId = playRequestRef.current + 1;
    playRequestRef.current = requestId;
    stopSpectrumMonitor();
    syncedLyricsRef.current = [];
    setState((current) => ({
      ...current,
      currentSong: song,
      queue,
      currentIndex: index,
      paused: false,
      positionSeconds: 0,
      durationSeconds: song.durationSeconds ?? null,
      lyricLine: "[LOADING LYRICS]",
      lyricsText: "",
      spectrumLevels: IDLE_SPECTRUM_LEVELS
    }));

    void playerClient.lyrics(song.id)
      .then((lyrics) => {
        if (playRequestRef.current !== requestId) {
          return;
        }
        const syncedLines = parseSyncedLyrics(lyrics.syncedText);
        const fallbackLine = firstLyricLine(lyrics.plainText || lyrics.syncedText);
        syncedLyricsRef.current = syncedLines;
        const positionSeconds = audioRef.current?.currentTime ?? 0;
        const lyricLine = lyricLineAt(positionSeconds, syncedLines) ?? fallbackLine;
        setState((current) => ({
          ...current,
          lyricLine: lyricLine || "[LYRICS UNAVAILABLE]",
          lyricsText: lyrics.plainText || lyrics.syncedText || ""
        }));
      })
      .catch(() => {
        if (playRequestRef.current === requestId) {
          setState((current) => ({ ...current, lyricLine: "[LYRICS UNAVAILABLE]", lyricsText: "" }));
        }
      });

    try {
      const audio = audioRef.current;
      if (!audio) {
        throw new Error("播放器尚未初始化");
      }

      audio.pause();
      audio.src = playerClient.streamUrl(song.id);
      audio.currentTime = 0;
      audio.load();
      await audio.play();
      if (playRequestRef.current !== requestId) {
        return;
      }
      setState((current) => ({
        ...current,
        paused: false,
        lyricLine: current.lyricLine
      }));
      startSpectrumMonitor(audio);
    } catch (error) {
      if (playRequestRef.current === requestId) {
        stopSpectrumMonitor();
        setState((current) => ({
          ...current,
          paused: true,
          lyricLine: `[ERROR: ${errorMessage(error)}]`,
          spectrumLevels: IDLE_SPECTRUM_LEVELS
        }));
      }
      throw error;
    }
  }

  return {
    state,
    playSong: async (song: Song) => {
      const existingIndex = state.queue.findIndex((item) => item.id === song.id);
      const nextQueue = existingIndex >= 0 ? state.queue : [...state.queue, song];
      const nextIndex = existingIndex >= 0 ? existingIndex : nextQueue.length - 1;
      await startPlayback(song, nextQueue, nextIndex);
    },
    playQueueIndex: async (index: number) => {
      const song = state.queue[index];
      if (!song) {
        return;
      }
      await startPlayback(song, state.queue, index);
    },
    addToQueue: (song: Song) => {
      setState((current) => {
        if (current.queue.some((item) => item.id === song.id)) {
          return current;
        }
        return { ...current, queue: [...current.queue, song] };
      });
    },
    removeFromQueue: (songId: string) => {
      setState((current) => {
        const index = current.queue.findIndex((song) => song.id === songId);
        if (index === -1) {
          return current;
        }
        const queue = current.queue.filter((song) => song.id !== songId);
        const currentRemoved = current.currentSong?.id === songId;
        if (currentRemoved) {
          syncedLyricsRef.current = [];
        }
        const nextIndex = currentRemoved ? -1 : index < current.currentIndex ? current.currentIndex - 1 : current.currentIndex;
        return {
          ...current,
          queue,
          currentIndex: nextIndex,
          currentSong: currentRemoved ? null : current.currentSong,
          paused: currentRemoved ? true : current.paused,
          lyricLine: currentRemoved ? "[NO TRACK]" : current.lyricLine,
          lyricsText: currentRemoved ? "" : current.lyricsText,
          spectrumLevels: currentRemoved ? IDLE_SPECTRUM_LEVELS : current.spectrumLevels
        };
      });
    },
    previous: async () => {
      const current = stateRef.current;
      if (current.queue.length === 0) {
        return;
      }
      const index = Math.max(0, current.currentIndex - 1);
      await startPlayback(current.queue[index], current.queue, index);
    },
    next: async () => {
      const current = stateRef.current;
      const index = nextQueueIndex(current);
      if (index < 0) {
        return;
      }
      await startPlayback(current.queue[index], current.queue, index);
    },
    togglePaused: () => {
      if (!state.currentSong) {
        return;
      }
      const audio = audioRef.current;
      if (!audio) {
        setState((current) => ({ ...current, paused: true, lyricLine: "[PLAYER NOT READY]" }));
        return;
      }
      if (state.paused) {
        audio.play()
          .then(() => {
            setState((current) => ({ ...current, paused: false }));
            startSpectrumMonitor(audio);
          })
          .catch((error) => setState((current) => ({
            ...current,
            paused: true,
            lyricLine: `[ERROR: ${errorMessage(error)}]`,
            spectrumLevels: IDLE_SPECTRUM_LEVELS
          })));
        return;
      }
      audio.pause();
      stopSpectrumMonitor();
      setState((current) => ({ ...current, paused: true, spectrumLevels: IDLE_SPECTRUM_LEVELS }));
    },
    nextMode: () => {
      setState((current) => {
        const index = PLAYBACK_MODES.indexOf(current.playbackMode);
        return { ...current, playbackMode: PLAYBACK_MODES[(index + 1) % PLAYBACK_MODES.length] };
      });
    }
  };
}

function nextQueueIndex(state: PlayerState) {
  if (state.queue.length === 0) {
    return -1;
  }
  if (state.playbackMode === "REPEAT_ONE" && state.currentIndex >= 0) {
    return state.currentIndex;
  }
  if (state.playbackMode === "SHUFFLE") {
    if (state.queue.length === 1) {
      return state.currentIndex >= 0 ? state.currentIndex : 0;
    }
    let index = Math.floor(Math.random() * state.queue.length);
    if (index === state.currentIndex) {
      index = (index + 1) % state.queue.length;
    }
    return index;
  }
  const next = state.currentIndex + 1;
  if (next < state.queue.length) {
    return next;
  }
  return state.playbackMode === "REPEAT_ALL" ? 0 : -1;
}

function fallbackSpectrum(timestamp: number) {
  return IDLE_SPECTRUM_LEVELS.map((level, index) => {
    const slow = Math.sin(timestamp / 260 + index * 0.74);
    const fast = Math.sin(timestamp / 118 + index * 1.37);
    const motion = 0.58 + slow * 0.26 + fast * 0.16;
    return clampLevel(level * motion);
  });
}

function clampLevel(level: number) {
  return Math.max(6, Math.min(100, Math.round(level)));
}

function logarithmicFrequency(position: number, minHz: number, maxHz: number) {
  return minHz * Math.pow(maxHz / minHz, position);
}

function firstLyricLine(text: string) {
  return text
    .split(/\r?\n/)
    .map((line) => line.replace(/\[[^\]]+]/g, "").trim())
    .find(Boolean) ?? "";
}

function parseSyncedLyrics(text: string) {
  const timePattern = /\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]/g;
  return text
    .split(/\r?\n/)
    .flatMap((line) => {
      const matches = [...line.matchAll(timePattern)];
      if (matches.length === 0) {
        return [];
      }
      const lyricText = line.replace(timePattern, "").trim();
      if (!lyricText) {
        return [];
      }
      return matches.map((match) => ({
        timeSeconds: lyricTimestamp(match),
        text: lyricText
      }));
    })
    .filter((line) => Number.isFinite(line.timeSeconds))
    .sort((first, second) => first.timeSeconds - second.timeSeconds);
}

function lyricTimestamp(match: RegExpMatchArray) {
  const minutes = Number.parseInt(match[1] ?? "0", 10);
  const seconds = Number.parseInt(match[2] ?? "0", 10);
  const fraction = match[3] ?? "";
  const fractionSeconds = fraction ? Number.parseInt(fraction, 10) / Math.pow(10, fraction.length) : 0;
  return minutes * 60 + seconds + fractionSeconds;
}

function lyricLineAt(positionSeconds: number, lines: SyncedLyricLine[]) {
  if (lines.length === 0) {
    return null;
  }

  const adjustedPosition = positionSeconds + 0.18;
  let low = 0;
  let high = lines.length - 1;
  let matchIndex = -1;
  while (low <= high) {
    const middle = Math.floor((low + high) / 2);
    if (lines[middle].timeSeconds <= adjustedPosition) {
      matchIndex = middle;
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }

  return matchIndex >= 0 ? lines[matchIndex].text : null;
}

function errorMessage(error: unknown) {
  return error instanceof Error && error.message ? error.message : "未知错误";
}
