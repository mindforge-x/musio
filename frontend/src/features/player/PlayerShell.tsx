import { useEffect, useState } from "react";
import { PlayerState } from "../../shared/types";
import { LyricLine } from "./LyricLine";
import { PlayerControls } from "./PlayerControls";
import { ProgressBar } from "./ProgressBar";

type PlayerShellProps = {
  state: PlayerState;
  onTogglePaused: () => void;
  onNextMode: () => void;
};

export function PlayerShell({ state, onTogglePaused, onNextMode }: PlayerShellProps) {
  const song = state.currentSong;
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  return (
    <section className="panel player-panel">
      <div className="player-hero">
        <div className="player-time">{formatClock(now)}</div>
        <p>{formatDate(now)}</p>
        <span className="live-indicator">ON AIR</span>
      </div>
      <div className="player-now-row">
        <div className="player-art">{song?.artworkUrl ? <img src={song.artworkUrl} alt="" /> : <span>Musio</span>}</div>
        <div className="player-main">
          <div className="player-title-row">
            <div>
              <h2>{song?.title || "等待播放"}</h2>
              <p>{song?.artists?.join(", ") || "从 Agent 结果或 Musio 歌单中选择一首歌"}</p>
            </div>
            <span>{state.queue.length} QUEUED</span>
          </div>
          <ProgressBar positionSeconds={state.positionSeconds} durationSeconds={state.durationSeconds} />
          <LyricLine text={state.lyricLine} />
        </div>
        <PlayerControls
          paused={state.paused}
          playbackMode={state.playbackMode}
          onTogglePaused={onTogglePaused}
          onNextMode={onNextMode}
        />
      </div>
    </section>
  );
}

function formatClock(date: Date) {
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(date);
}

function formatDate(date: Date) {
  return new Intl.DateTimeFormat("en-US", {
    weekday: "long",
    month: "short",
    day: "2-digit",
    year: "numeric"
  }).format(date);
}
