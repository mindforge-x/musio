import { type FormEvent, useEffect, useMemo, useState } from "react";
import { Music2, Plus, Sparkles, X } from "lucide-react";
import { EventLog, MusioPlaylist, MusioPlaylistItem, Song } from "../../shared/types";
import { musioPlaylistClient } from "./musioPlaylistClient";
import { MusioPlaylistDetail } from "./MusioPlaylistDetail";

type MusioPlaylistsPageProps = {
  currentSongId?: string | null;
  disabledReason?: string | null;
  onPlayPlaylist: (songs: Song[], startIndex: number, playlistName: string) => void;
  onAddSongsToQueue: (songs: Song[], sourceLabel: string) => void;
  onEvent: (event: EventLog) => void;
};

const PLAYLIST_NAME_MAX_LENGTH = 50;
const PLAYLIST_DESCRIPTION_MAX_LENGTH = 200;

export function MusioPlaylistsPage({
  currentSongId,
  disabledReason,
  onPlayPlaylist,
  onAddSongsToQueue,
  onEvent
}: MusioPlaylistsPageProps) {
  const [playlists, setPlaylists] = useState<MusioPlaylist[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [operationItemId, setOperationItemId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [creatingPlaylist, setCreatingPlaylist] = useState(false);

  useEffect(() => {
    musioPlaylistClient.list()
      .then((items) => {
        setPlaylists(items);
        setSelectedId((current) => items.some((item) => item.id === current) ? current : items[0]?.id ?? null);
      })
      .catch(() => {
        setPlaylists([]);
        onEvent({ id: crypto.randomUUID(), name: "playlist", detail: "Musio 歌单读取失败" });
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!createOpen) {
      return;
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !creatingPlaylist) {
        closeCreateDialog();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [createOpen, creatingPlaylist]);

  const selected = playlists.find((item) => item.id === selectedId) ?? null;
  const selectedItems = useMemo(() => selected ? orderedPlaylistItems(selected) : [], [selected]);
  const selectedSongs = useMemo(() => selectedItems.map(playlistItemToSong), [selectedItems]);
  const totalTrackCount = useMemo(
    () => playlists.reduce((total, playlist) => total + playlist.items.length, 0),
    [playlists]
  );
  const canCreatePlaylist = createName.trim().length > 0 && !creatingPlaylist;

  function playSelectedPlaylist(startIndex: number) {
    if (!selected || selectedSongs.length === 0) {
      return;
    }
    if (disabledReason) {
      onEvent({ id: crypto.randomUUID(), name: "source", detail: disabledReason });
      return;
    }
    onPlayPlaylist(selectedSongs, startIndex, selected.name);
  }

  function addSelectedSongsToQueue(songs: Song[], sourceLabel: string) {
    if (songs.length === 0) {
      return;
    }
    onAddSongsToQueue(songs, sourceLabel);
  }

  async function removeItem(item: MusioPlaylistItem) {
    setOperationItemId(item.id);
    try {
      const updated = await musioPlaylistClient.removeItem(item.playlistId, item.id);
      setPlaylists((current) => current.map((playlist) => playlist.id === updated.id ? updated : playlist));
      onEvent({ id: crypto.randomUUID(), name: "playlist", detail: `已从 Musio 歌单移除：${item.title || item.providerTrackId}` });
    } catch (error) {
      const detail = error instanceof Error && error.message ? error.message : "未知错误";
      onEvent({ id: crypto.randomUUID(), name: "playlist", detail: `移除失败：${detail}` });
    } finally {
      setOperationItemId(null);
    }
  }

  function resetCreateForm() {
    setCreateName("");
    setCreateDescription("");
  }

  function closeCreateDialog() {
    if (creatingPlaylist) {
      return;
    }
    setCreateOpen(false);
    resetCreateForm();
  }

  async function createPlaylist(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canCreatePlaylist) {
      return;
    }
    setCreatingPlaylist(true);
    try {
      const created = await musioPlaylistClient.create({
        name: createName,
        description: createDescription
      });
      setPlaylists((current) => [...current, created]);
      setSelectedId(created.id);
      resetCreateForm();
      setCreateOpen(false);
      onEvent({ id: crypto.randomUUID(), name: "playlist", detail: `已创建歌单：${created.name}` });
    } catch (error) {
      const detail = error instanceof Error && error.message ? error.message : "未知错误";
      onEvent({ id: crypto.randomUUID(), name: "playlist", detail: `创建歌单失败：${detail}` });
    } finally {
      setCreatingPlaylist(false);
    }
  }

  return (
    <section className="panel musio-playlists-panel nothing-playlists-panel">
      <div className="nothing-playlists-header">
        <div>
          <p className="eyebrow">LOCAL PLAYLIST STORAGE</p>
          <h2>本地收藏库</h2>
        </div>
        <div className="nothing-playlist-metrics" aria-label="Musio 歌单统计">
          <span><strong>{loading ? "--" : playlists.length}</strong> PLAYLISTS</span>
          <span><strong>{loading ? "--" : totalTrackCount}</strong> TRACKS</span>
          <span className={disabledReason ? "offline" : "online"}>{disabledReason ? "SOURCE LOCKED" : "SOURCE READY"}</span>
        </div>
      </div>
      <div className="musio-playlists-layout">
        <div className="musio-playlist-list" aria-label="Musio 歌单列表">
          <button
            type="button"
            className="musio-playlist-create-toggle"
            onClick={() => setCreateOpen(true)}
            aria-expanded={createOpen}
          >
            <span className="musio-playlist-index">
              <Plus size={18} />
            </span>
            <span className="musio-playlist-name">新建本地歌单</span>
            <small>CREATE PLAYLIST</small>
          </button>
          {playlists.length === 0 ? (
            <p className="empty-copy">{loading ? "读取 Musio 歌单中。" : "还没有 Musio 歌单。"}</p>
          ) : (
            playlists.map((playlist, index) => (
              <button
                type="button"
                key={playlist.id}
                className={playlist.id === selectedId ? "selected" : ""}
                onClick={() => setSelectedId(playlist.id)}
              >
                <span className="musio-playlist-index">{String(index + 1).padStart(2, "0")}</span>
                <span className="musio-playlist-name">{playlist.name}</span>
                <small>{playlist.items.length} TRACKS</small>
              </button>
            ))
          )}
        </div>
        <MusioPlaylistDetail
          playlist={selected}
          items={selectedItems}
          currentSongId={currentSongId}
          operationItemId={operationItemId}
          onPlayAll={() => playSelectedPlaylist(0)}
          onAddAllToQueue={() => addSelectedSongsToQueue(selectedSongs, selected?.name ?? "Musio 歌单")}
          onPlayItem={playSelectedPlaylist}
          onAddItemToQueue={(index) => {
            const song = selectedSongs[index];
            if (song) {
              addSelectedSongsToQueue([song], song.title || song.id);
            }
          }}
          onRemoveItem={removeItem}
        />
      </div>
      {createOpen ? (
        <div
          className="musio-playlist-modal-overlay"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              closeCreateDialog();
            }
          }}
        >
          <section
            className="musio-playlist-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="musio-playlist-create-title"
          >
            <div className="musio-playlist-modal-header">
              <div className="musio-playlist-modal-title">
                <Sparkles size={30} />
                <h3 id="musio-playlist-create-title">新建本地歌单</h3>
                <p>为你的音乐收藏创建一个新的歌单</p>
              </div>
              <button
                type="button"
                className="musio-playlist-modal-close"
                aria-label="关闭新建歌单弹窗"
                onClick={closeCreateDialog}
                disabled={creatingPlaylist}
              >
                <X size={16} />
              </button>
            </div>
            <form className="musio-playlist-create-form" onSubmit={createPlaylist}>
              <label>
                <span className="musio-playlist-field-label">歌单名称</span>
                <div className="musio-playlist-field-control">
                  <input
                    value={createName}
                    onChange={(event) => setCreateName(event.target.value)}
                    placeholder="例如：深夜代码"
                    maxLength={PLAYLIST_NAME_MAX_LENGTH}
                    autoFocus
                  />
                  <span className="musio-playlist-field-count">
                    {createName.length}/{PLAYLIST_NAME_MAX_LENGTH}
                  </span>
                </div>
              </label>
              <label>
                <span className="musio-playlist-field-label">描述（可选）</span>
                <div className="musio-playlist-field-control">
                  <textarea
                    value={createDescription}
                    onChange={(event) => setCreateDescription(event.target.value)}
                    placeholder="说点关于这个歌单的故事吧..."
                    maxLength={PLAYLIST_DESCRIPTION_MAX_LENGTH}
                    rows={4}
                  />
                  <span className="musio-playlist-field-count">
                    {createDescription.length}/{PLAYLIST_DESCRIPTION_MAX_LENGTH}
                  </span>
                </div>
              </label>
              <div className="musio-playlist-create-footer">
                <div className="musio-playlist-create-ornament" aria-hidden="true">
                  <span />
                  <Music2 size={25} />
                  <span />
                </div>
                <div className="musio-playlist-create-actions">
                  <button type="submit" className="primary" disabled={!canCreatePlaylist}>
                    <Plus size={18} />
                    <span>{creatingPlaylist ? "创建中" : "创建歌单"}</span>
                  </button>
                </div>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function orderedPlaylistItems(playlist: MusioPlaylist) {
  return [...playlist.items].sort((first, second) => first.sortOrder - second.sortOrder);
}

function playlistItemToSong(item: MusioPlaylistItem): Song {
  return {
    id: item.providerTrackId,
    provider: item.provider,
    title: item.title || item.providerTrackId,
    artists: item.artists ?? [],
    album: item.album ?? null,
    durationSeconds: item.durationSeconds ?? null,
    artworkUrl: item.artworkUrl ?? null
  };
}
