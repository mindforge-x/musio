import { Pause, Play, Repeat, SkipBack, SkipForward } from "lucide-react";
import { PlaybackMode } from "../../shared/types";

type PlayerControlsProps = {
  paused: boolean;
  playbackMode: PlaybackMode;
  onTogglePaused: () => void;
  onNextMode: () => void;
};

export function PlayerControls({ paused, playbackMode, onTogglePaused, onNextMode }: PlayerControlsProps) {
  return (
    <div className="player-controls">
      <button type="button" aria-label="Previous song">
        <SkipBack size={18} />
      </button>
      <button type="button" aria-label={paused ? "Play" : "Pause"} onClick={onTogglePaused}>
        {paused ? <Play size={18} /> : <Pause size={18} />}
      </button>
      <button type="button" aria-label="Next song">
        <SkipForward size={18} />
      </button>
      <button type="button" aria-label="Playback mode" onClick={onNextMode}>
        <Repeat size={18} />
        <span>{playbackMode.toLowerCase()}</span>
      </button>
    </div>
  );
}
