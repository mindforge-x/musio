type ProgressBarProps = {
  positionSeconds: number;
  durationSeconds: number | null;
};

export function ProgressBar({ positionSeconds, durationSeconds }: ProgressBarProps) {
  const progress = durationSeconds ? Math.min(100, Math.round((positionSeconds / durationSeconds) * 100)) : 0;

  return (
    <div className="progress-row">
      <span>{formatTime(positionSeconds)}</span>
      <div className="progress-track" aria-label="Playback progress">
        <div style={{ width: `${progress}%` }} />
      </div>
      <span>{formatTime(durationSeconds ?? 0)}</span>
    </div>
  );
}

function formatTime(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const rest = Math.floor(seconds % 60).toString().padStart(2, "0");
  return `${minutes}:${rest}`;
}
