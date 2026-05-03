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
        {levels.map((level, index) => (
          <i
            key={index}
            style={{
              height: `${level}%`,
              opacity: 0.34 + (level / 100) * 0.58
            }}
          />
        ))}
      </div>
    </div>
  );
}
