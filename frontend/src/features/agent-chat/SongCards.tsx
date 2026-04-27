import { FormEvent, useState } from "react";
import { Play, Search } from "lucide-react";
import { api } from "../../shared/api";
import { EventLog, Song } from "../../shared/types";

type SongCardsProps = {
  busy: boolean;
  songs: Song[];
  onSongs: (songs: Song[]) => void;
  onBusyChange: (busy: boolean) => void;
  onEvent: (event: EventLog) => void;
  onPlaySong: (song: Song) => void;
};

export function SongCards({ busy, songs, onSongs, onBusyChange, onEvent, onPlaySong }: SongCardsProps) {
  const [searchKeyword, setSearchKeyword] = useState("city pop");

  async function search(event: FormEvent) {
    event.preventDefault();
    if (!searchKeyword.trim()) {
      return;
    }

    onBusyChange(true);
    try {
      const result = await api.search(searchKeyword);
      onSongs(result);
      onEvent({ id: crypto.randomUUID(), name: "search", detail: `returned ${result.length} songs` });
    } finally {
      onBusyChange(false);
    }
  }

  return (
    <section className="panel search-panel">
      <div className="panel-heading">
        <h2>Catalog</h2>
        <span>{songs.length} results</span>
      </div>
      <form onSubmit={search} className="search-form">
        <Search size={18} />
        <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} />
        <button type="submit" disabled={busy || !searchKeyword.trim()}>
          Search
        </button>
      </form>
      <div className="song-list">
        {songs.map((song) => (
          <article key={song.id} className="song-row">
            <div>
              <strong>{song.title || song.id}</strong>
              <span>{song.artists?.join(", ") || song.provider || "QQ Music"}</span>
            </div>
            <button type="button" aria-label={`Play ${song.title || song.id}`} onClick={() => onPlaySong(song)}>
              <Play size={16} />
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}
