import { FormEvent, useState } from "react";
import { Play, Search } from "lucide-react";
import { api } from "../../shared/api";
import { EventLog, Song } from "../../shared/types";

type SongCardsProps = {
  busy: boolean;
  disabledReason?: string | null;
  songs: Song[];
  onSongs: (songs: Song[]) => void;
  onBusyChange: (busy: boolean) => void;
  onEvent: (event: EventLog) => void;
  onPlaySong: (song: Song) => void;
};

export function SongCards({ busy, disabledReason, songs, onSongs, onBusyChange, onEvent, onPlaySong }: SongCardsProps) {
  const [searchKeyword, setSearchKeyword] = useState("周杰伦");

  async function search(event: FormEvent) {
    event.preventDefault();
    if (disabledReason) {
      onEvent({ id: crypto.randomUUID(), name: "source", detail: disabledReason });
      return;
    }
    if (!searchKeyword.trim()) {
      return;
    }

    onBusyChange(true);
    try {
      const result = await api.search(searchKeyword);
      onSongs(result);
      onEvent({ id: crypto.randomUUID(), name: "search", detail: `搜索返回 ${result.length} 首歌曲` });
    } finally {
      onBusyChange(false);
    }
  }

  return (
    <section className="panel search-panel">
      <div className="panel-heading">
        <h2>歌曲结果</h2>
        <span>{songs.length} 条结果</span>
      </div>
      {disabledReason ? <p className="access-note">{disabledReason}</p> : null}
      <form onSubmit={search} className="search-form">
        <Search size={18} />
        <input value={searchKeyword} onChange={(event) => setSearchKeyword(event.target.value)} />
        <button type="submit" disabled={busy || Boolean(disabledReason) || !searchKeyword.trim()}>
          搜索
        </button>
      </form>
      <div className="song-list">
        {songs.map((song) => (
          <article key={song.id} className="song-row">
            <div>
              <strong>{song.title || song.id}</strong>
              <span>{song.artists?.join(", ") || song.provider || "QQ 音乐"}</span>
            </div>
            <button type="button" aria-label={`播放 ${song.title || song.id}`} onClick={() => onPlaySong(song)}>
              <Play size={16} />
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}
