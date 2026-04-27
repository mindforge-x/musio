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

  return (
    <section className="panel player-panel">
      <div className="player-art">{song?.artworkUrl ? <img src={song.artworkUrl} alt="" /> : <span>Musio</span>}</div>
      <div className="player-main">
        <div className="player-title-row">
          <div>
            <h2>{song?.title || "Nothing playing"}</h2>
            <p>{song?.artists?.join(", ") || "Choose a song from Agent results or Musio playlists"}</p>
          </div>
          <span>{state.queue.length} queued</span>
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
    </section>
  );
}
