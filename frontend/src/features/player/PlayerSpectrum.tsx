type PlayerSpectrumProps = {
  levels: number[];
};

export function PlayerSpectrum({ levels }: PlayerSpectrumProps) {
  const peak = Math.max(...levels, 0);

  return (
    <div className="player-spectrum-frame" aria-hidden="true">
      <div className="spectrum-meta">
        <span>SUB</span>
        <span>LOW</span>
        <span>MID</span>
        <span>AIR</span>
        <span>{peak.toString().padStart(3, "0")} PEAK</span>
      </div>
      <div className="player-spectrum">
        {levels.map((level, index) => {
          const normalizedLevel = Math.max(0.04, Math.min(level, 100) / 100);
          return (
            <i
              key={index}
              style={{
                height: "100%",
                opacity: 0.34 + normalizedLevel * 0.58,
                transform: `scaleY(${normalizedLevel})`
              }}
            />
          );
        })}
      </div>
    </div>
  );
}
