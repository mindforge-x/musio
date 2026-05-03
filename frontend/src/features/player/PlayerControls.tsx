import { Pause, Play, Repeat, SkipBack, SkipForward } from "lucide-react";
import { PlaybackMode } from "../../shared/types";

type PlayerControlsProps = {
  paused: boolean;
  playbackMode: PlaybackMode;
  canPrevious: boolean;
  canNext: boolean;
  onTogglePaused: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onNextMode: () => void;
};

export function PlayerControls({
  paused,
  playbackMode,
  canPrevious,
  canNext,
  onTogglePaused,
  onPrevious,
  onNext,
  onNextMode
}: PlayerControlsProps) {
  return (
    <div className="player-controls">
      <button type="button" aria-label="上一首" onClick={onPrevious} disabled={!canPrevious}>
        <SkipBack size={18} />
      </button>
      <button type="button" aria-label={paused ? "播放" : "暂停"} onClick={onTogglePaused}>
        {paused ? <Play size={18} /> : <Pause size={18} />}
      </button>
      <button type="button" aria-label="下一首" onClick={onNext} disabled={!canNext}>
        <SkipForward size={18} />
      </button>
      <button type="button" aria-label="播放顺序" onClick={onNextMode}>
        <Repeat size={18} />
        <span>{playbackModeLabel(playbackMode)}</span>
      </button>
    </div>
  );
}

function playbackModeLabel(mode: PlaybackMode) {
  switch (mode) {
    case "SEQUENTIAL":
      return "顺序";
    case "REPEAT_ONE":
      return "单曲";
    case "REPEAT_ALL":
      return "循环";
    case "SHUFFLE":
      return "随机";
  }
}
