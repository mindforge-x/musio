type LyricLineProps = {
  text: string;
};

export function LyricLine({ text }: LyricLineProps) {
  return <p className="lyric-line">{text}</p>;
}
