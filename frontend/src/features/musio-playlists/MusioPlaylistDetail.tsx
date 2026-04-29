import { MusioPlaylist } from "../../shared/types";

type MusioPlaylistDetailProps = {
  playlist: MusioPlaylist | null;
};

export function MusioPlaylistDetail({ playlist }: MusioPlaylistDetailProps) {
  if (!playlist) {
    return <p className="empty-copy">选择一个 Musio 歌单查看跨音乐源歌曲。</p>;
  }

  return (
    <div className="song-list">
      {playlist.items.map((item) => (
        <article className="song-row" key={item.id}>
          <div>
            <strong>{item.title}</strong>
            <span>{item.provider}: {item.artists.join(", ")}</span>
          </div>
        </article>
      ))}
    </div>
  );
}
