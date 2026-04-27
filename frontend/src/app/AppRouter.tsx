import { useCallback, useEffect, useMemo, useState } from "react";
import { Activity, Headphones, ListMusic, MessageSquare, Radio } from "lucide-react";
import { api } from "../shared/api";
import { EventLog, Song, SystemStatus } from "../shared/types";
import { AgentChatPanel } from "../features/agent-chat/AgentChatPanel";
import { AgentEvents } from "../features/agent-chat/AgentEvents";
import { SongCards } from "../features/agent-chat/SongCards";
import { MusioPlaylistsPage } from "../features/musio-playlists/MusioPlaylistsPage";
import { PlayerShell } from "../features/player/PlayerShell";
import { usePlayerStore } from "../features/player/playerStore";
import { SourceSetupPage } from "../features/source-setup/SourceSetupPage";
import { AppRoute } from "./routes";

export function AppRouter() {
  const [route, setRoute] = useState<AppRoute>("workbench");
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [events, setEvents] = useState<EventLog[]>([]);
  const [songs, setSongs] = useState<Song[]>([]);
  const [busy, setBusy] = useState(false);
  const player = usePlayerStore();

  useEffect(() => {
    api.status()
      .then(setStatus)
      .catch(() => setStatus(null));
  }, []);

  const backendLabel = useMemo(() => status?.backend ?? "offline", [status]);
  const addEvent = useCallback((event: EventLog) => {
    setEvents((current) => [event, ...current]);
  }, []);

  function playSong(song: Song) {
    player.playSong(song);
    addEvent({ id: crypto.randomUUID(), name: "player", detail: `queued ${song.title || song.id}` });
  }

  return (
    <main className="app-shell">
      <aside className="rail">
        <div className="brand-mark">
          <Headphones size={24} strokeWidth={2.4} />
        </div>
        <button
          className={`rail-button ${route === "workbench" ? "active" : ""}`}
          aria-label="Chat"
          onClick={() => setRoute("workbench")}
        >
          <MessageSquare size={20} />
        </button>
        <button
          className={`rail-button ${route === "playlists" ? "active" : ""}`}
          aria-label="Library"
          onClick={() => setRoute("playlists")}
        >
          <ListMusic size={20} />
        </button>
        <button className="rail-button" aria-label="Playback">
          <Radio size={20} />
        </button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Musio local agent</p>
            <h1>Music operations console</h1>
            <p className="config-line">
              {status ? `${status.aiProvider} / ${status.aiModel}` : "model config unavailable"}
            </p>
          </div>
          <div className={`status-pill ${backendLabel === "ok" ? "online" : ""}`}>
            <Activity size={16} />
            <span>{backendLabel}</span>
          </div>
        </header>

        <PlayerShell state={player.state} onTogglePaused={player.togglePaused} onNextMode={player.nextMode} />

        {route === "playlists" ? (
          <MusioPlaylistsPage />
        ) : (
          <div className="grid">
            <AgentChatPanel busy={busy} onBusyChange={setBusy} onEvent={addEvent} onSongs={setSongs} />
            <SourceSetupPage busy={busy} onBusyChange={setBusy} onEvent={addEvent} />
            <SongCards
              busy={busy}
              songs={songs}
              onSongs={setSongs}
              onBusyChange={setBusy}
              onEvent={addEvent}
              onPlaySong={playSong}
            />
            <AgentEvents events={events} onClear={() => setEvents([])} />
          </div>
        )}
      </section>
    </main>
  );
}
