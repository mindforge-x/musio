import { useCallback, useEffect, useMemo, useState } from "react";
import { Activity, Cable, ListMusic, MessageSquare } from "lucide-react";
import { api } from "../shared/api";
import { EventLog, ProviderStatus, Song, SystemStatus } from "../shared/types";
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
  const [providerStatuses, setProviderStatuses] = useState<ProviderStatus[]>([]);
  const [events, setEvents] = useState<EventLog[]>([]);
  const [songs, setSongs] = useState<Song[]>([]);
  const [busy, setBusy] = useState(false);
  const selectedSources = useMemo(() => selectedSourcesFromUrl(), []);
  const player = usePlayerStore();

  const refreshProviderStatuses = useCallback(() => {
    api.providers()
      .then(setProviderStatuses)
      .catch(() => setProviderStatuses([]));
  }, []);

  useEffect(() => {
    api.status()
      .then(setStatus)
      .catch(() => setStatus(null));
    refreshProviderStatuses();
  }, [refreshProviderStatuses]);

  const backendLabel = useMemo(() => status?.backend ?? "offline", [status]);
  const qqMusicStatus = useMemo(
    () => providerStatuses.find((item) => sourceKey(item.provider) === "qqmusic") ?? null,
    [providerStatuses]
  );
  const backendDisplayLabel = backendLabel === "ok" ? "正常" : "离线";
  const selectedSourcesLabel = useMemo(() => selectedSources.map(sourceDisplayName).join(" / "), [selectedSources]);
  const qqMusicConnectionLabel = qqMusicStatus ? providerConnectionLabel(qqMusicStatus) : "QQ 音乐状态未知";
  const musicOperationDisabledReason = qqMusicStatus?.authenticated
    ? null
    : `${qqMusicConnectionLabel}。需要先连接 QQ 音乐才能搜索和播放。`;
  const pageTitle = route === "setup" ? "音乐源激活" : route === "playlists" ? "Musio 歌单" : "播放控制台";
  const pageSubcopy = route === "setup"
    ? "激活本次启动选择的音乐源。允许部分登录，也可以进入受限模式。"
    : `${status ? `${status.aiProvider} / ${status.aiModel}` : "模型配置不可用"} · ${selectedSourcesLabel} · ${qqMusicConnectionLabel}`;
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
        {route !== "workbench" ? (
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
        ) : null}

        {route === "setup" ? (
          <SourceSetupPage
            busy={busy}
            selectedSources={selectedSources}
            onBusyChange={setBusy}
            onEvent={addEvent}
            onProviderStatusesChange={setProviderStatuses}
            onContinue={() => {
              refreshProviderStatuses();
              setRoute("workbench");
            }}
          />
        ) : route === "playlists" ? (
          <MusioPlaylistsPage />
        ) : (
          <section className="radio-workbench">
            <header className="radio-header">
              <div className="radio-brand">
                <div className="radio-avatar">M</div>
                <div>
                  <p>Musio FM</p>
                  <strong>Musio</strong>
                </div>
              </div>
              <div className="radio-header-actions">
                <span className={`radio-state ${backendLabel === "ok" ? "online" : ""}`}>{backendDisplayLabel}</span>
                <span>{status ? status.aiModel : "MODEL OFFLINE"}</span>
              </div>
            </header>
            <PlayerShell state={player.state} onTogglePaused={player.togglePaused} onNextMode={player.nextMode} />
            <div className="radio-queue-strip">
              <span>QUEUE</span>
              <span>{player.state.queue.length} TRACKS</span>
            </div>
            <div className="agent-workspace">
              <AgentChatPanel
                busy={busy}
                disabledReason={musicOperationDisabledReason}
                onBusyChange={setBusy}
                onEvent={addEvent}
                onPlaySong={playSong}
              />
              <div className="agent-side-stack">
                <SongCards
                  busy={busy}
                  disabledReason={musicOperationDisabledReason}
                  songs={songs}
                  onSongs={setSongs}
                  onBusyChange={setBusy}
                  onEvent={addEvent}
                  onPlaySong={playSong}
                />
                <AgentEvents events={events} onClear={() => setEvents([])} />
              </div>
            </div>
            <footer className="radio-footer">
              <span>MUSIO FM</span>
              <span>{qqMusicConnectionLabel}</span>
            </footer>
          </section>
        )}
      </section>
    </main>
  );
}

function sourceKey(source: string) {
  const normalized = source.replace(/[_-]/g, "").toLowerCase();
  if (normalized === "qqmusic" || normalized === "qq") {
    return "qqmusic";
  }
  return normalized;
}

function providerConnectionLabel(status: ProviderStatus) {
  if (status.authenticated) {
    return status.musicGeneState === "READY" ? "QQ 音乐已连接，音乐基因已就绪" : "QQ 音乐已连接，音乐基因待生成";
  }
  switch (status.connectionState) {
    case "EXPIRED":
      return "QQ 音乐登录已过期";
    case "UNVERIFIED":
      return "QQ 音乐等待校验";
    case "NOT_LOGGED_IN":
      return "QQ 音乐未连接";
    default:
      return "QQ 音乐未连接";
  }
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
