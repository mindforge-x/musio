import { useCallback, useEffect, useMemo, useState } from "react";
import { Activity, Cable, ListMusic, MessageSquare } from "lucide-react";
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
  const [route, setRoute] = useState<AppRoute>(() => initialRouteFromUrl());
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [events, setEvents] = useState<EventLog[]>([]);
  const [songs, setSongs] = useState<Song[]>([]);
  const [busy, setBusy] = useState(false);
  const selectedSources = useMemo(() => selectedSourcesFromUrl(), []);
  const player = usePlayerStore();

  useEffect(() => {
    api.status()
      .then(setStatus)
      .catch(() => setStatus(null));
  }, []);

  const backendLabel = useMemo(() => status?.backend ?? "offline", [status]);
  const backendDisplayLabel = backendLabel === "ok" ? "正常" : "离线";
  const selectedSourcesLabel = useMemo(() => selectedSources.map(sourceDisplayName).join(" / "), [selectedSources]);
  const pageTitle = route === "setup" ? "音乐源激活" : route === "playlists" ? "Musio 歌单" : "播放控制台";
  const pageSubcopy = route === "setup"
    ? "激活本次启动选择的音乐源。允许部分登录，也可以进入受限模式。"
    : `${status ? `${status.aiProvider} / ${status.aiModel}` : "模型配置不可用"} · ${selectedSourcesLabel}`;
  const addEvent = useCallback((event: EventLog) => {
    setEvents((current) => [event, ...current]);
  }, []);

  function playSong(song: Song) {
    player.playSong(song);
    addEvent({ id: crypto.randomUUID(), name: "player", detail: `已加入队列：${song.title || song.id}` });
  }

  return (
    <main className="app-shell">
      <aside className="rail">
        <div className="brand-mark">
          <span>M</span>
        </div>
        <button
          className={`rail-button ${route === "setup" ? "active" : ""}`}
          aria-label="音乐源"
          onClick={() => setRoute("setup")}
        >
          <Cable size={20} />
        </button>
        <button
          className={`rail-button ${route === "workbench" ? "active" : ""}`}
          aria-label="Agent 工作台"
          onClick={() => setRoute("workbench")}
        >
          <MessageSquare size={20} />
        </button>
        <button
          className={`rail-button ${route === "playlists" ? "active" : ""}`}
          aria-label="Musio 歌单"
          onClick={() => setRoute("playlists")}
        >
          <ListMusic size={20} />
        </button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">Musio 本地音乐 Agent</p>
            <h1>{pageTitle}</h1>
            <p className="config-line">{pageSubcopy}</p>
          </div>
          <div className={`status-pill ${backendLabel === "ok" ? "online" : ""}`}>
            <Activity size={16} />
            <span>{backendDisplayLabel}</span>
          </div>
        </header>

        {route === "setup" ? (
          <SourceSetupPage
            busy={busy}
            selectedSources={selectedSources}
            onBusyChange={setBusy}
            onEvent={addEvent}
            onContinue={() => setRoute("workbench")}
          />
        ) : route === "playlists" ? (
          <MusioPlaylistsPage />
        ) : (
          <>
            <PlayerShell state={player.state} onTogglePaused={player.togglePaused} onNextMode={player.nextMode} />
            <div className="agent-workspace">
              <AgentChatPanel busy={busy} onBusyChange={setBusy} onEvent={addEvent} onSongs={setSongs} />
              <div className="agent-side-stack">
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
            </div>
          </>
        )}
      </section>
    </main>
  );
}

function sourceDisplayName(source: string) {
  switch (source) {
    case "qqmusic":
      return "QQ 音乐";
    case "netease":
      return "网易云音乐";
    case "local":
      return "本地音乐";
    default:
      return source;
  }
}

function initialRouteFromUrl(): AppRoute {
  return new URLSearchParams(window.location.search).has("sources") ? "setup" : "workbench";
}

function selectedSourcesFromUrl(): string[] {
  const params = new URLSearchParams(window.location.search);
  const raw = params.get("sources");
  if (!raw) {
    return ["qqmusic"];
  }
  return raw.split(",")
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
}
