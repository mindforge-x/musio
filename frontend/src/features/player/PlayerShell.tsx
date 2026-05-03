import { PlayerState } from "../../shared/types";
import { LyricLine } from "./LyricLine";
import { PlayerControls } from "./PlayerControls";
import { ProgressBar } from "./ProgressBar";

type PlayerShellProps = {
  state: PlayerState;
  onTogglePaused: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onNextMode: () => void;
};

export function PlayerShell({ state, onTogglePaused, onPrevious, onNext, onNextMode }: PlayerShellProps) {
  const song = state.currentSong;
  const canPrevious = state.queue.length > 0 && state.currentIndex > 0;
  const canNext = state.queue.length > 0 && (state.currentIndex < state.queue.length - 1 || state.playbackMode === "REPEAT_ALL" || state.playbackMode === "SHUFFLE");

  return (
    <section className="player-panel" aria-label="Musio 播放器">
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
          <LyricLine text={state.lyricLine || "[NO TRACK]"} />
          <ProgressBar positionSeconds={state.positionSeconds} durationSeconds={state.durationSeconds} />
        </div>
        <PlayerControls
          paused={state.paused}
          playbackMode={state.playbackMode}
          canPrevious={canPrevious}
          canNext={canNext}
          onTogglePaused={onTogglePaused}
          onPrevious={onPrevious}
          onNext={onNext}
          onNextMode={onNextMode}
        />
      </div>
    </section>
  );
}
