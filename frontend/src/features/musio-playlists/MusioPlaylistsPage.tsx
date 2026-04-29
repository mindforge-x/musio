import { useEffect, useState } from "react";
import { MusioPlaylist } from "../../shared/types";
import { musioPlaylistClient } from "./musioPlaylistClient";
import { MusioPlaylistDetail } from "./MusioPlaylistDetail";

export function MusioPlaylistsPage() {
  const [playlists, setPlaylists] = useState<MusioPlaylist[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    musioPlaylistClient.list()
      .then((items) => {
        setPlaylists(items);
        setSelectedId((current) => current ?? items[0]?.id ?? null);
      })
      .catch(() => setPlaylists([]));
  }, []);

  const selected = playlists.find((item) => item.id === selectedId) ?? null;

  return (
    <section className="panel musio-playlists-panel">
      <div className="panel-heading">
        <h2>Musio 歌单</h2>
        <span>{playlists.length} 个歌单</span>
      </div>
      <div className="musio-playlists-layout">
        <div className="musio-playlist-list">
          {playlists.length === 0 ? (
            <p className="empty-copy">还没有 Musio 歌单。</p>
          ) : (
            playlists.map((playlist) => (
              <button
                type="button"
                key={playlist.id}
                className={playlist.id === selectedId ? "selected" : ""}
                onClick={() => setSelectedId(playlist.id)}
              >
                {playlist.name}
              </button>
            ))
          )}
        </div>
        <MusioPlaylistDetail playlist={selected} />
      </div>
    </section>
  );
}
