import { MusioPlaylist } from "../../shared/types";

type MusioPlaylistDetailProps = {
  playlist: MusioPlaylist | null;
};

export function MusioPlaylistDetail({ playlist }: MusioPlaylistDetailProps) {
  if (!playlist) {
    return <p className="empty-copy">Select a Musio playlist to inspect its cross-source songs.</p>;
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
